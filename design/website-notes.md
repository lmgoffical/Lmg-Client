# Website design notes

Why the site looks the way it does, so future changes keep it feeling hand-made.

## What made v1 look "AI-generated" (and the fix)
| Pattern | Fix |
|---|---|
| Centered glowing logo + marketing tagline | Left-aligned hero in first person ("I got bored of every client looking the same...") next to a product shot |
| Stat cards showing 0 / 0 / 0 / 0 | Stats appear as one line of text, only once they're above zero |
| Six identical feature cards with blue dots | Uneven two-column list, short honest notes, a few coloured left rules |
| Glow and radial gradients on everything | Flat colours; one blue for actions, one warm gold (the totem) for highlights |
| Inter everywhere | Bricolage Grotesque (headings), IBM Plex Sans (body), Silkscreen pixel font for small labels only |
| No sign of a real project | Real changelog, honest FAQ, footer that says who made it |
| Robotic empty states | Plain human copy ("Quiet in here. Start something?") |

## Rules
- **Voice:** first person, short sentences, no hype words ("revolutionary", "seamless", "unleash").
- **Colour:** `--blue` only for things you can click or the current page. `--gold` for versions, codes, pinned tags and the one sticker. Nothing glows.
- **Type:** pixel font only below ~12px for labels, versions and codes - never for paragraphs.
- **Contrast:** smallest text uses `--faint` (#7c8ba3, about 4.6:1 on panels). Don't go darker.
- **Product shot:** the hero menu is HTML/CSS mimicking the real in-game menu. Replace it with real screenshots once there are good ones.
- **Keep it a little imperfect:** slight rotations on the product shot and sticker, the hand-drawn underline. Don't add more than that.
- **Changelog:** update `CHANGELOG` in `docs/app.js` with every release.
