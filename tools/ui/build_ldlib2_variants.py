"""Materialize three editable comparisons using identical layout, content and font size."""
from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[2]
DEST=ROOT/'src/ldlib2/resources/assets/mtrnav/ui/ldlib2'
base=(DEST/'navigation.xml').read_text(encoding='utf-8')
native='''
    #workspace { background: rect(#dd171717); }
    .titlebar { background: empty; }
    button:host { base-background: vanilla-sprite(minecraft:widget/button); hover-background: vanilla-sprite(minecraft:widget/button_highlighted); pressed-background: vanilla-sprite(minecraft:widget/button_highlighted); }
    button .__button_text__ { text-color: #ffffff; text-shadow: true; }
    .primary:host, .selected:host { base-background: vanilla-sprite(minecraft:widget/button); hover-background: vanilla-sprite(minecraft:widget/button_highlighted); }
    button.__disabled__:host { base-background: vanilla-sprite(minecraft:widget/button_disabled); }
    button.__disabled__ .__button_text__ { text-color: #a0a0a0; }
    text-field:host { background: vanilla-sprite(minecraft:widget/text_field); focus-overlay: vanilla-sprite(minecraft:widget/text_field_highlighted); text-color: #ffffff; }
    .inset { background: rect(#b0000000); }
    .journey:host { base-background: rect(#40202020); hover-background: group(rect(#80303030),border(1,#ffffffff)); }
    .journey.selected:host { base-background: group(rect(#80505050),border(1,#ffffffff)); }
    #compact-tabs button:host { base-background: vanilla-sprite(minecraft:widget/tab); hover-background: vanilla-sprite(minecraft:widget/tab_highlighted); }
    #compact-tabs button.selected:host { base-background: vanilla-sprite(minecraft:widget/tab_selected); hover-background: vanilla-sprite(minecraft:widget/tab_selected_highlighted); }
'''
editor='''
    #workspace { background: built-in(ui-mc:RECT_2); }
    .titlebar { background: built-in(ui-mc:RECT_2); }
    .inset { background: built-in(ui-mc:RECT_BLACK); }
'''
create='''
    #workspace { background: sprite(mtrnav:textures/gui/create/stock_keeper.png,22,48,212,20,12,4,12,4); }
    .titlebar { background: sprite(mtrnav:textures/gui/create/stock_keeper.png,37,0,182,16,3,3,3,3); }
    .titlebar label { text-color: #42352b; }
    button:host { base-background: sprite(mtrnav:textures/gui/create/widgets.png,0,0,18,18,3,3,3,3); hover-background: sprite(mtrnav:textures/gui/create/widgets.png,18,0,18,18,3,3,3,3); pressed-background: sprite(mtrnav:textures/gui/create/widgets.png,36,0,18,18,3,3,3,3); }
    button .__button_text__ { text-color: #eeeeee; }
    .primary:host, .selected:host { base-background: sprite(mtrnav:textures/gui/create/widgets.png,72,0,18,18,3,3,3,3); hover-background: sprite(mtrnav:textures/gui/create/widgets.png,54,0,18,18,3,3,3,3); }
    button.__disabled__:host { base-background: sprite(mtrnav:textures/gui/create/widgets.png,90,0,18,18,3,3,3,3); }
    text-field:host { background: group(rect(#f7eed9),border(1,#4a2d31)); text-color: #39362b; cursor-color: #39362b; }
    .inset { background: group(sprite(mtrnav:textures/gui/create/schedule.png,32,32,32,32,0,0,0,0,#ffffff,repeat),border(1,#161616)); }
    .journey:host { base-background: group(rect(#dd30332f),border(1,#6b6d5c)); hover-background: group(rect(#dd414c3b),border(1,#c8aa72)); }
    .journey.selected:host { base-background: group(rect(#dd3d4b36),border(1,#dec58d)); }
    .brass { text-color: #dec58d; }
'''
for name,styles in [('native',native),('editor',editor),('create',create)]:
    xml=base.replace(']]></style>',styles+'  ]]></style>',1)
    (DEST/f'navigation-{name}.xml').write_text(xml,encoding='utf-8')
    print('Updated comparison XML:',name)
