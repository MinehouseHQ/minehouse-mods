import io
import json
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent


class ElementalResources(unittest.TestCase):
    def test_bake_contains_frames_anchored_to_native_limbs(self):
        from bake_elemental import bake
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / 'assets/minehouse-menu'
            bake(HERE.parent / 'models/elemental', root)
            for k in ('ember', 'rime', 'end'):
                for part in ('right_arm', 'left_arm', 'right_leg', 'left_leg', 'right_arm_wide', 'left_arm_wide'):
                    for i in (0, 1, 47, 95):
                        key = f'elemental/{k}/{part}/f{i}'
                        item = json.loads((root / 'items' / (key + '.json')).read_bytes())
                        model = json.loads((root / 'models' / (key + '.json')).read_bytes())
                        self.assertEqual(item['model']['model'], 'minehouse-menu:' + key)
                        self.assertEqual(item['model']['type'], 'minecraft:model')
                        shell = next(el for el in model['elements'] if el.get('name') == part.removesuffix('_wide') + '_surface')
                        self.assertLess(shell['from'][1], 0)
                        self.assertAlmostEqual(shell['from'][2], 5.72, places=3)
                        self.assertAlmostEqual(shell['to'][0]-shell['from'][0], (4.56 if part.endswith('_wide') else 3.56) if 'arm' in part else 4.248, places=3)
                self.assertTrue((root / f'textures/item/elemental/{k}.png').read_bytes().startswith(b'\x89PNG\r\n\x1a\n'))
                for i in (0, 12, 36):
                    key = f'elemental/{k}/step/f{i}'
                    self.assertTrue((root / 'items' / (key + '.json')).is_file())
            p=root/'models/elemental/end/right_arm/f0.json'
            q=root/'models/elemental/end/right_arm/f26.json'
            self.assertNotEqual(p.read_bytes(),q.read_bytes())


if __name__ == '__main__':
    unittest.main()
