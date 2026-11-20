# SkyBlock Girlfriend

SkyBlock Girlfriend is a client-side companion mod for Minecraft, intended for
Hypixel SkyBlock players but usable anywhere. The miniature copy of your player
uses your current skin, follows behind you, and looks toward you. It does not
add a companion to the world, send packets, or change server gameplay. Its
movement helper is local to the client and is never added to the world.

## Development

Use Java 25 and launch the Fabric development client with:

```powershell
.\gradlew.bat runClient
```

Join any single-player or multiplayer world to see the companion. Build the
installable mod with:

```powershell
.\gradlew.bat build
```

## Installation

Copy `build\libs\skyblock-girlfriend-1.0.0.jar` to the `mods` folder of a
Minecraft 26.2 Fabric installation using Fabric Loader 0.19.5 or newer. Install
Fabric API for Minecraft 26.2 in the same instance. Restart Minecraft and enter
a world; the companion appears automatically. To verify it, move and turn in
the world: the miniature should walk to a position behind you, remain close,
and keep its head and body turned toward you.

Press **G** in a world to open companion settings. The key can also be changed
in Minecraft's Controls settings. The Appearance section lets you open a
searchable, scrollable card list with a live preview. Type part of a filename in
the **Search skins...** field to filter the cards; each card shows its preview
and name below it, with `.png` omitted and long names scrolling horizontally.
Click one to select it; the selected skin has a green outline. Put 64x64 PNG
skins directly in the Minecraft instance's `skins`
folder; the picker reports how many it found and shows the folder path if it is
missing. Imported skin arm width is inferred from its texture, and sleeve
overlays remain enabled. The menu also lets you set the companion size from 30%
to 200% (50% by default) or hide the companion. Scale, visibility, and selected
skin are stored under
`config\skyblock-girlfriend\` in that instance. Press G or Escape to close the
menu. The companion uses Minecraft's ground mob navigation and jump physics to
follow over elevation changes, waits until you are more than three blocks away
before pursuing, sprints while chasing, and teleports only when more than 20
blocks away. Its gait is smoothed between client ticks. The hidden movement
helper is ticked locally for vanilla movement physics but is never added to the
world, rendered, or sent to the server. The rendered companion stays grounded
when you jump and does not render armor or held items.

The mod is licensed under CC0.

## Messaging

Client-side companion messages appear in a queued notification with the selected
skin's head, stay fully visible for five seconds, and then fade out. Press **G**
and choose **GUI** to drag the preview or resize it by a corner. **Reset Position**
restores its default position. Notification size and normalized position are
saved in `config\skyblock-girlfriend\settings.properties`.

The normal player inventory has a conversation panel at the far-left edge. Drag
its four-arrow handle to reposition it, or drag a corner to resize it. It stays
left of the inventory slots and remembers its position and size. Set the
companion's display name in the settings menu; that name appears above its
messages. Resizing changes the available text area, not the font or portrait
size. The panel supports mouse-wheel scrolling and a draggable scrollbar, and
stays at the latest message unless you have scrolled up to older ones.
Conversation history remains in memory across world changes and reconnections,
but is cleared when Minecraft restarts and is never written to disk. The message
panel includes a message field with a Send button; click the field, type, and
press Enter or click Send. Player messages appear on the right with your skin
face, while companion replies appear on the left. The companion can calculate
expressions using `+`, `-`, `/`, `*` or `x`, parentheses, and `k`/`m` number
suffixes (`3k + 58k` becomes `61,000`). Commas in input are ignored. This
feature works locally and does not use an LLM or API key. Notifications are
suppressed while the message field is focused; messages still appear in the
conversation history. Invalid calculations receive a randomly selected playful
reply.

Death messages are detected only on `hypixel.net` or a subdomain while the
sidebar identifies SkyBlock. The detector recognizes the local-player
system-message format (`☠ You ...`), including kill, fall, and environmental
death reasons, and also checks the local death screen. A short duplicate
cooldown applies before adding `HOW DID YOU DIE?!`.
