using UnityEngine;
using UnityEngine.Events;

/// <summary>
/// Optional aim-assist mechanic for a Unity game you own/control.
/// Put this on the player camera or a player controller object.
/// Targets must have HeadAimTarget and a head Transform.
/// </summary>
public class HeadshotAimController : MonoBehaviour
{
    [Header("Aim")]
    [SerializeField] private Camera aimCamera;
    [SerializeField, Min(0f)] private float maxDistance = 120f;
    [SerializeField, Range(1f, 180f)] private float fieldOfViewDegrees = 18f;
    [SerializeField, Min(0f)] private float turnSpeed = 540f;
    [SerializeField] private LayerMask targetLayers = ~0;
    [SerializeField] private LayerMask obstructionLayers = ~0;
    [SerializeField] private bool requireLineOfSight = true;

    [Header("Optional auto-fire for your own game's weapon")]
    [SerializeField] private bool autoFire;
    [SerializeField, Min(0.05f)] private float fireInterval = 0.2f;
    [SerializeField, Range(0.1f, 10f)] private float fireAlignmentDegrees = 1.2f;
    [SerializeField] private UnityEvent onAutoFire;

    private float nextFireTime;
    private HeadAimTarget currentTarget;

    public HeadAimTarget CurrentTarget => currentTarget;

    private void Reset()
    {
        aimCamera = GetComponentInChildren<Camera>();
    }

    private void Awake()
    {
        if (aimCamera == null)
            aimCamera = GetComponentInChildren<Camera>();

        if (aimCamera == null)
            Debug.LogError("HeadshotAimController: assign the player's aim camera.", this);
    }

    private void Update()
    {
        if (aimCamera == null) return;

        currentTarget = FindBestTarget();
        if (currentTarget == null || currentTarget.Head == null) return;

        Vector3 toHead = currentTarget.Head.position - aimCamera.transform.position;
        if (toHead.sqrMagnitude < 0.0001f) return;

        Quaternion desiredRotation = Quaternion.LookRotation(toHead.normalized, Vector3.up);
        aimCamera.transform.rotation = Quaternion.RotateTowards(
            aimCamera.transform.rotation, desiredRotation, turnSpeed * Time.deltaTime);

        if (autoFire && Time.time >= nextFireTime)
        {
            float alignment = Vector3.Angle(aimCamera.transform.forward, toHead);
            if (alignment <= fireAlignmentDegrees && HasClearLine(currentTarget))
            {
                onAutoFire?.Invoke();
                nextFireTime = Time.time + fireInterval;
            }
        }
    }

    private HeadAimTarget FindBestTarget()
    {
        HeadAimTarget best = null;
        float bestAngle = fieldOfViewDegrees;

        HeadAimTarget[] targets = FindObjectsByType<HeadAimTarget>(FindObjectsSortMode.None);
        Vector3 origin = aimCamera.transform.position;

        foreach (HeadAimTarget target in targets)
        {
            if (target == null || !target.isActiveAndEnabled || target.Head == null) continue;
            if (target.transform.root == transform.root) continue;

            Vector3 offset = target.Head.position - origin;
            float distance = offset.magnitude;
            if (distance <= 0.01f || distance > maxDistance) continue;

            float angle = Vector3.Angle(aimCamera.transform.forward, offset);
            if (angle > bestAngle) continue;
            if (!IsInTargetLayers(target)) continue;
            if (requireLineOfSight && !HasClearLine(target)) continue;

            best = target;
            bestAngle = angle;
        }

        return best;
    }

    private bool IsInTargetLayers(HeadAimTarget target)
    {
        return (targetLayers.value & (1 << target.gameObject.layer)) != 0;
    }

    private bool HasClearLine(HeadAimTarget target)
    {
        if (target == null || target.Head == null) return false;

        Vector3 origin = aimCamera.transform.position;
        Vector3 offset = target.Head.position - origin;
        float distance = offset.magnitude;
        if (distance <= 0.01f || distance > maxDistance) return false;

        if (!Physics.Raycast(origin, offset.normalized, out RaycastHit hit,
                distance, obstructionLayers, QueryTriggerInteraction.Ignore))
            return true;

        return hit.transform == target.Head ||
               hit.transform.IsChildOf(target.Head) ||
               hit.transform.IsChildOf(target.transform);
    }

    private void OnDrawGizmosSelected()
    {
        Camera cam = aimCamera != null ? aimCamera : GetComponentInChildren<Camera>();
        if (cam == null) return;

        Gizmos.color = Color.yellow;
        Gizmos.DrawRay(cam.transform.position, cam.transform.forward * maxDistance);
        if (currentTarget != null && currentTarget.Head != null)
        {
            Gizmos.color = Color.red;
            Gizmos.DrawLine(cam.transform.position, currentTarget.Head.position);
            Gizmos.DrawWireSphere(currentTarget.Head.position, 0.12f);
        }
    }
}
