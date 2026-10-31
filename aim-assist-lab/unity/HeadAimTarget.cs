using UnityEngine;

/// <summary>
/// Marks the head point for aim-training and hit-detection tests in your own Unity game.
/// Assign the head Transform in the Inspector. Add a collider to the head if your
/// weapon uses physics raycasts and configure the weapon to register head hits separately.
/// </summary>
public class HeadAimTarget : MonoBehaviour
{
    [SerializeField] private Transform head;
    public Transform Head => head;

    private void OnDrawGizmosSelected()
    {
        if (head == null) return;
        Gizmos.color = Color.red;
        Gizmos.DrawWireSphere(head.position, 0.12f);
        Gizmos.DrawLine(head.position, head.position + Vector3.up * 0.25f);
    }
}
