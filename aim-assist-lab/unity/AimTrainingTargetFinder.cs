using UnityEngine;

/// <summary>
/// Target-awareness helper for an aim-training mode in a Unity game you own.
/// Finds the visible head point nearest to the center of the camera view and
/// exposes it for a reticle, scoring, or training feedback. It does not rotate
/// the camera or fire the weapon.
/// </summary>
public class AimTrainingTargetFinder : MonoBehaviour
{
    [SerializeField] private Camera aimCamera;
    [SerializeField, Min(1f)] private float maxDistance = 120f;
    [SerializeField, Range(1f, 90f)] private float searchAngleDegrees = 20f;
    [SerializeField] private LayerMask targetLayers = ~0;
    [SerializeField] private LayerMask obstructionLayers = ~0;

    public HeadAimTarget CurrentTarget { get; private set; }
    public Vector3 CurrentHeadPoint => CurrentTarget != null && CurrentTarget.Head != null
        ? CurrentTarget.Head.position : Vector3.zero;
    public float CurrentAngleDegrees { get; private set; }

    private void Awake()
    {
        if (aimCamera == null) aimCamera = GetComponentInChildren<Camera>();
    }

    private void Update()
    {
        CurrentTarget = null;
        CurrentAngleDegrees = 0f;
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

            float angle = Vector3.Angle(aimCamera.transform.forward, offset);
            if (angle > bestAngle) continue;

            if (Physics.Raycast(origin, offset.normalized, out RaycastHit hit, distance,
                    obstructionLayers, QueryTriggerInteraction.Ignore))
            {
                bool hitTarget = hit.transform == target.Head ||
                                 hit.transform.IsChildOf(target.Head) ||
                                 hit.transform.IsChildOf(target.transform);
                if (!hitTarget) continue;
            }

            bestAngle = angle;
            CurrentTarget = target;
            CurrentAngleDegrees = angle;
        }
    }

    private void OnDrawGizmosSelected()
    {
        if (CurrentTarget == null || CurrentTarget.Head == null) return;
        Gizmos.color = Color.cyan;
        Gizmos.DrawLine(transform.position, CurrentTarget.Head.position);
        Gizmos.DrawWireSphere(CurrentTarget.Head.position, 0.14f);
    }
}
