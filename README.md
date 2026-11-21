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

Press **G** in a world to open companion settings. The name field controls the
live nametag above the companion. The key can also be changed in Minecraft's
Controls settings. **Super Secret Settings** opens companion-only appearance
controls; Female Gender Mod is not required, and these options do not change
your player. Adjust the textured shape's size, spacing, height, depth, and
angle, then enable or tune its bounce and floppiness. Its texture is projected
from the corresponding pixels of the selected skin's torso and outer layer, so
clothing details follow changes to the shape's position and angle. These
settings are saved with the companion's other options. The Appearance section lets you open a
searchable, scrollable card list with a live preview. Type part of a filename
in the **Search skins...** field to filter the cards; each card shows its preview
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
and choose **GUI** to edit the Chat and Notification previews. Toggle either
preview to hide it while editing the other; this does not disable that feature.
Drag a visible preview to move it or use its corners to resize it. Selected
previews have a white outline. **Reset Position** resets the currently visible
previews. Positions and sizes are saved in
`config\skyblock-girlfriend\settings.properties`.

The normal player inventory has a conversation panel at the far-left edge,
beside the inventory slots. Its position and size are edited from the GUI
screen, not by dragging the live panel. The header shows the selected companion
face and name. Click the calculator icon to switch between the main AI chat and
the separate Math chat; the selected chat stays active when closing and
reopening the inventory, and a new game launch starts in the AI chat. The panel
supports mouse-wheel scrolling and a draggable scrollbar, and follows new
messages unless you have scrolled up to older ones.

The main chat supports Groq, OpenAI, and local Ollama. Press **G** and choose
**LLM Setup** to select a service, endpoint, model, and API key, then enable AI.
The default Groq model is `openai/gpt-oss-120b`; OpenAI defaults to
`gpt-4.1-mini`, and Ollama defaults to `llama3.2:3b` at `localhost:11434`.
Ollama must be running locally and have the selected model installed. API keys
are saved in plain text in the instance's local
`config\skyblock-girlfriend\llm.properties` file. The mod does not bundle or log
keys; for hosted providers, it sends the key and AI chat messages to the
selected service with each request. Ollama requests stay on localhost.
Groq GPT-OSS uses low reasoning effort and returns only the visible answer to
the chat; provider HTTP errors and empty replies include available status and
token-usage details.

The Math chat works locally without an LLM. It calculates expressions using
`+`, `-`, `/`, `*` or `x`, parentheses, and `k`/`m` number suffixes
(`3k + 58k` becomes `61,000`). Commas in input are ignored. Invalid calculations
receive a randomly selected playful reply. Chat histories remain in memory
across world changes and reconnections, but are cleared when Minecraft restarts
and are never written to disk. Notifications are suppressed while the message
field is focused; messages still appear in conversation history. Player
messages appear on the right with your skin face, while companion replies appear
on the left.

Death messages are detected only on `hypixel.net` or a subdomain while the
sidebar identifies SkyBlock. The detector recognizes the local-player
system-message format (`☠ You ...`), including kill, fall, and environmental
death reasons, and also checks the local death screen. A short duplicate
cooldown applies before adding `HOW DID YOU DIE?!`.
