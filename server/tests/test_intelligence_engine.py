import unittest
from intelligence_engine import analyze_intelligence


class IntelligenceEngineTests(unittest.TestCase):
    def test_insufficient_history_abstains(self):
        result = analyze_intelligence([1.2, 1.4, 2.0, 1.0])
        self.assertEqual(result["status"], "collecting_data")
        self.assertIsNone(result["point_forecast"])

    def test_walk_forward_and_thresholds(self):
        history = [1.0 + ((i * 37) % 900) / 100 for i in range(180)]
        result = analyze_intelligence(history)
        self.assertEqual(result["sample_count"], 180)
        self.assertGreater(result["walk_forward_tests"], 0)
        self.assertIn("2", result["thresholds"])
        self.assertIn("wilson_95_percent", result["thresholds"]["2"])
        self.assertIn(result["selected_model"], (
            "median_100", "median_20", "geometric_20", "log_ewma"
        ))

    def test_one_x_is_valid_data(self):
        result = analyze_intelligence([1.0] * 40)
        self.assertEqual(result["sample_count"], 40)
        self.assertEqual(result["thresholds"]["2"]["observations"], 0)


if __name__ == "__main__":
    unittest.main()
