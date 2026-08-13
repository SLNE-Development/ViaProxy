import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
EGG_PATH = ROOT / "pterodactyl" / "egg-viaproxy.json"
EXPECTED_STARTUP = (
    "java -Xms128M -XX:MaxRAMPercentage=95.0 -DskipUpdateCheck=true "
    "-jar ViaProxy.jar config viaproxy.yml"
)


class ViaProxyEggTest(unittest.TestCase):
    def load_egg(self):
        return json.loads(EGG_PATH.read_text(encoding="utf-8"))

    def test_runtime_contract(self):
        egg = self.load_egg()
        self.assertEqual(EXPECTED_STARTUP, egg["startup"])
        self.assertEqual("stop", egg["config"]["stop"])
        self.assertIsInstance(egg["config"]["startup"], str)
        self.assertEqual(
            {"done": "Binding proxy server to "},
            json.loads(egg["config"]["startup"]),
        )
        self.assertEqual([], egg["variables"])
        self.assertTrue(any("java_25" in image for image in egg["docker_images"].values()))

    def test_installer_uses_latest_slne_release_and_exact_asset(self):
        script = self.load_egg()["scripts"]["installation"]["script"]
        self.assertIn(
            "https://api.github.com/repos/SLNE-Development/ViaProxy/releases/latest",
            script,
        )
        self.assertIn('select(.name == "ViaProxy.jar")', script)
        self.assertIn("browser_download_url", script)
        self.assertNotIn("ci.viaversion.com", script)
        self.assertNotIn("VIAPROXY_CHANNEL", script)

    def test_installer_is_atomic_and_rejects_empty_downloads(self):
        script = self.load_egg()["scripts"]["installation"]["script"]
        self.assertIn("ViaProxy.jar.tmp", script)
        self.assertIn('[ ! -s "${TMP_JAR}" ]', script)
        self.assertIn('mv -f "${TMP_JAR}" "${FINAL_JAR}"', script)
        self.assertIn("trap", script)

    def test_runtime_never_downloads_or_updates(self):
        startup = self.load_egg()["startup"].lower()
        for forbidden in ("curl", "wget", "github", "jenkins"):
            self.assertNotIn(forbidden, startup)


if __name__ == "__main__":
    unittest.main(verbosity=2)
