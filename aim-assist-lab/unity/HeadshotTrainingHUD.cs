using UnityEngine;

/// <summary>
/// Lightweight on-screen training HUD for a Unity project you own.
/// Displays the selected visible head point and angle-to-center feedback.
/// It deliberately does not steer the camera or trigger weapons.
/// </summary>
public class HeadshotTrainingHUD : MonoBehaviour
{
    [SerializeField] private AimTrainingTargetFinder targetFinder;
    [SerializeField] private Camera aimCamera;
    [SerializeField] private bool showDiagnostics = true;
    [SerializeField, Range(4f, 32f)] private float markerRadius = 10f;
    [SerializeField] private Color markerColor = Color.red;

    private GUIStyle labelStyle;
    private GUIStyle titleStyle;

    private void Awake()
    {
        if (targetFinder == null) targetFinder = GetComponent<AimTrainingTargetFinder>();
        if (aimCamera == null) aimCamera = GetComponentInChildren<Camera>();
        labelStyle = new GUIStyle { fontSize = 18, normal = { textColor = Color.white } };
        titleStyle = new GUIStyle { fontSize = 22, fontStyle = FontStyle.Bold, normal = { textColor = new Color(1f, 0.55f, 0.12f) } };
    }

    private void OnGUI()
    {
        if (targetFinder == null || aimCamera == null) return;

        if (showDiagnostics)
        {
            GUI.Label(new Rect(18, 18, 280, 34), "AIM TRAINING", titleStyle);
            string info = targetFinder.CurrentTarget == null
                ? "No visible head target"
                : $"Angle error: {targetFinder.CurrentAngleDegrees:0.00}°";
            GUI.Label(new Rect(18, 52, 320, 30), info, labelStyle);
        }

        if (targetFinder.CurrentTarget == null) return;
        Vector3 screen = aimCamera.WorldToScreenPoint(targetFinder.CurrentHeadPoint);
        if (screen.z <= 0f) return;

        float x = screen.x;
        float y = Screen.height - screen.y;
        Color previous = GUI.color;
        GUI.color = markerColor;
        GUI.DrawTexture(new Rect(x - markerRadius, y - 1f, markerRadius * 2f, 2f), Texture2D.whiteTexture);
        GUI.DrawTexture(new Rect(x - 1f, y - markerRadius, 2f, markerRadius * 2f), Texture2D.whiteTexture);
        GUI.color = previous;
    }
}
