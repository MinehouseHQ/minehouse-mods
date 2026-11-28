"""Bake the approved Blockbench elemental effects as client-only Java 26.3 items."""
import argparse
import copy
import json
from pathlib import Path

from bbanim import Project

KINDS = ('ember', 'rime', 'end')
PIVOTS = dict(right_arm=(-5, 22, 0), left_arm=(5, 22, 0), right_leg=(-1.9, 12, 0), left_leg=(1.9, 12, 0))
FPS = 20
NS = 'minehouse-menu'


def emit(path, payload):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(payload)


def write_json(path, data):
    emit(path, (json.dumps(data, separators=(',', ':')) + '\n').encode())


def walk_ids(node):
    if isinstance(node, str):
        yield node
    else:
        for child in node['children']:
            yield from walk_ids(child)


def limb_project(source, name, wide=False):
    model = copy.deepcopy(source)
    groups = {g['uuid']: g for g in model['groups']}
    root = model['outliner'][0]
    root['children'] = [n for n in root['children'] if groups[n['uuid']]['name'] == name]
    assert len(root['children']) == 1
    elements = set(walk_ids(root))
    model['elements'] = [el for el in model['elements'] if el['uuid'] in elements]
    if wide:
        # Move exterior controls with the extra pixel; widen broad faces without changing the inner edge.
        s = -1 if name == 'right_arm' else 1
        offset = {'accent_1': .5, 'accent_2': 1, 'accent_3': .5, 'running_highlight_1': .5,
                  'liquid': .7, 'drop_neck': .7, 'drop_fall': .7, 'mote_1': .5, 'mote_2': 1, 'mote_3': .5, 'rim': .5}
        suffix = dict(blade_1='accent_1', blade_2='accent_2', blade_3='accent_3', chip_1='mote_1', chip_2='mote_2',
                      chip_3='mote_3', streak_1='running_highlight_1', liquid_neck='drop_neck', liquid_drop='drop_fall')
        for g in groups.values():
            if g['name'].startswith(name+'_'):
                g['origin'][0] += s * offset.get(g['name'].removeprefix(name+'_'), 0)
        for el in model['elements']:
            tag = el['name'].removeprefix(name+'_')
            if tag == 'surface':
                el['origin'][0] += s*.5
                el['from' if s == -1 else 'to'][0] += s
            else:
                for key in ('origin', 'from', 'to'):
                    el[key][0] += s * offset.get(suffix.get(tag,''), 0)
    return model


def frame_models(model, pivot, where, root, texture, once):
    p = Project(model)
    # Java FIXED centers the item. Item-space +Y up, native ModelPart local +Y down.
    p.shift = [8-a for a in pivot]
    clip = p.animation('footfall' if once else 'idle')
    tracks = p.tracks(clip)
    frames = int(round(FPS * clip['length']))
    assert frames == (36 if once else 96)
    for i in range(frames+int(once)):
        t = i / FPS
        boxes = p.elements(tracks, t)
        assert len(boxes) == len(p.cubes)
        ready = []
        for src, box in zip(p.cubes, boxes):
            delta = [b-a for a,b in zip(box['from'], box['to'])]
            if sum(v > .001 for v in delta) < 2:
                continue  # hidden at this phase; avoid zero-area quads in the item buffer
            if not all(-16 <= val <= 32 for vec in (box['from'],box['to']) for val in vec):
                raise ValueError((where,i,src['name'],box))
            ready.append({'name':src['name'], **box})
        key = f'elemental/{where}/f{i}'
        if ready:
            write_json(root / f'items/{key}.json', {'model':{'type':'minecraft:model','model':NS+':'+key}})
        else:
            write_json(root / f'items/{key}.json', {'model':{'type':'minecraft:empty'}})
        write_json(root / f'models/{key}.json', {'ambientocclusion':False,'gui_light':'front','textures':{'0':texture, 'particle':'#0'},'elements':ready})


def bake(models, root):
    for kind in KINDS:
        base = json.loads((models / f'{kind}.bbmodel').read_bytes())
        project = Project(base)
        assert len(project.pngs) == 1
        emit(root / f'textures/item/elemental/{kind}.png', next(iter(project.pngs.values())))
        texture = f'{NS}:item/elemental/{kind}'
        for name,pivot in PIVOTS.items():
            for wide in ((False,True) if name.endswith('_arm') else (False,)):
                frame_models(limb_project(base,name,wide),pivot,f'{kind}/{name}'+('_wide' if wide else ''),root,texture,False)
        step = json.loads((models / f'step_{kind}.bbmodel').read_bytes())
        p = Project(step)
        assert len(p.pngs)==1
        emit(root / f'textures/item/elemental/step_{kind}.png',next(iter(p.pngs.values())))
        frame_models(step,(0,0,0),f'{kind}/step',root,f'{NS}:item/elemental/step_{kind}',True)


if __name__ == '__main__':
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument('source', type=Path)
    cli.add_argument('resources', type=Path)
    args = cli.parse_args()
    bake(args.source, args.resources / 'assets' / NS)
