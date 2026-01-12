# CloudFrame Resource Pack

This resource pack provides custom textures and models for CloudFrame items.

## Setup

1. Copy the `resourcepack` folder to your resource packs directory.
2. Open a texture editor (e.g., Photoshop, GIMP, or Aseprite).
3. Create 16x16 PNG textures for each item and place them in:
   - `assets/minecraft/textures/item/cloud_tube.png`
   - `assets/minecraft/textures/item/cloud_marker.png`
   - `assets/minecraft/textures/item/cloud_wrench.png`
   - `assets/minecraft/textures/item/quarry_controller.png`

4. In Minecraft, go to **Options → Resource Packs** and enable "CloudFrame Resource Pack".

## Custom Model Data Mapping

- **1001**: Cloud Tube (Copper Block base)
- **1002**: Cloud Marker (Blaze Rod base)
- **1003**: Cloud Wrench (Iron Hoe base)
- **1004**: Quarry Controller (Copper Block base)

## How It Works

The `pack.mcmeta` defines the pack format. The `copper_block.json` model file (parent model for Copper Block) uses overrides to detect custom model data and apply the appropriate custom model.

When a player has the resource pack enabled and receives a Cloud item (via crafting recipe), the item's custom model data triggers the override, displaying the custom texture instead of the vanilla texture.
