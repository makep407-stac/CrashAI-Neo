using UnityEngine;

/// <summary>
/// Finds the visible head point nearest to the center of the player's view.
/// Intended for a training HUD and aim-accuracy feedback in a Unity game you own.
/// Does not rotate the camera or fire weapons.
/// </summary>
public class AimTrainingTargetFinder : MonoBehaviour
{
    [SerializeField] private Camera aimCamera;
    [SerializeField, Min(1f)] private float maxDistance = 120f;
    [SerializeField, Range(1f, 90f)] private float searchAngleDegrees = 20f;
    [SerializeField] private LayerMask targetLayers = ~0;
    [SerializeField] private LayerMask obstructionLayers = ~0;
    [SerializeField, Min(0.02f)] private float refreshInterval = 0.08f;

    public HeadAimTarget CurrentTarget { get; private set; }
    public Vector3 CurrentHeadPoint => CurrentTarget != null && CurrentTarget.Head != null
        ? CurrentTarget.Head.position : Vector3.zero;
    public float CurrentAngleDegrees { get; private set; }
    public float CurrentDistance { get; private set; }
    private float nextRefresh;

    private void Awake()
    {
        if (aimCamera == null) aimCamera = GetComponentInChildren<Camera>();
    }

    private void Update()
    {
        if (Time.time < nextRefresh) return;
        nextRefresh = Time.time + refreshInterval;
        CurrentTarget = null;
        CurrentAngleDegrees = 0f;
        CurrentDistance = 0f;
        if (aimCamera == null) return;

        float bestAngle = searchAngleDegrees;
        Vector3 origin = aimCamera.transform.position;
        HeadAimTarget[] targets = FindObjectsByType<HeadAimTarget>(FindObjectsSortMode.None);

        foreach (HeadAimTarget target in targets)
        {
            if (target == null || !target.isActiveAndEnabled || target.Head == null) continue;
            if (target.transform.root == transform.root) continue;
            if ((targetLayers.value & (1 << target.gameObject.layer)) == 0) continue;

            Vector3 offset = target.Head.position - origin;
            float distance = offset.magnitude;
            if (distance < 0.01f || distance > maxDistance) continue;

            Vector3 screen = aimCamera.WorldToViewportPoint(target.Head.position);
            if (screen.z <= 0f || screen.x < 0f || screen.x > 1f || screen.y < 0f || screen.y > 1f) continue;

            float angle = Vector3.Angle(aimCamera.transform.forward, offset);
            if (angle > bestAngle || !HasLineOfSight(origin, offset, distance, target)) continue;

            bestAngle = angle;
            CurrentTarget = target;
            CurrentAngleDegrees = angle;
            CurrentDistance = distance;
        }
    }

    private bool HasLineOfSight(Vector3 origin, Vector3 offset, float distance, HeadAimTarget target)
    {
        if (!Physics.Raycast(origin, offset.normalized, out RaycastHit hit, distance,
                obstructionLayers, QueryTriggerInteraction.Ignore))
            return true;

        return hit.transform == target.Head ||
               hit.transform.IsChildOf(target.Head) ||
               hit.transform.IsChildOf(target.transform);
    }

    private void OnDrawGizmosSelected()
    {
        if (CurrentTarget == null || CurrentTarget.Head == null) return;
        Gizmos.color = Color.cyan;
        Gizmos.DrawLine(transform.position, CurrentTarget.Head.position);
        Gizmos.DrawWireSphere(CurrentTarget.Head.position, 0.14f);
    }
}
