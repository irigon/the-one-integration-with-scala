# -*- coding: utf-8 -*-
from typing import List, Union

class HostGroup:
    contents = ''
    okmap = ''

    def __init__(self, gid: str, delim: str = ''):
        self.set('groupID', gid + delim)

    def set_okmap(self, map_file: str):
        self.okmap = map_file

    def set(self, name: str, val: Union[str, int, float]):
        self.contents += 'Group{{nr}}.{name} = {val}\n'.format(
            name=name,
            val=val
        )

class ScenarioSettings:
    contents = ''
    groups = []

    def __init__(self, name: str):
        self.set('Scenario.name', name)

    def set(self, name, val):
        self.contents += '{name} = {val}\n'.format(
            name=name,
            val=val
        )

    def add_group(self, group: HostGroup):
        self.groups.append(group)

    def spacer(self):
        self.contents += '\n\n'

    def complete_groups(self):
        self.set('Scenario.nrofHostGroups', len(self.groups))
        self.spacer()
        maps = []
        for i, g in enumerate(self.groups, start=1):
            if g.okmap:
                if g.okmap not in maps:
                    maps.append(g.okmap)
                map_nr = maps.index(g.okmap) + 1
                g.set('okMaps', map_nr)

            self.contents += g.contents.format(nr=i)
            self.spacer()

        self.set('MapBasedMovement.nrofMapFiles', len(maps))
        for i, m in enumerate(maps, start=1):
            self.set('MapBasedMovement.mapFile{}'.format(i), m)

    def write(self, filename: str):
        with open(filename, 'w+') as fp:
            fp.write(self.contents)
