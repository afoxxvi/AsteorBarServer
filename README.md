# AsteorBar for Spigot server

Made to support [AsteorBar](https://github.com/afoxxvi/AsteorBar) Forge mod. Sync player's saturation and exhaustion level to client, so that the AsteorBar mod can display these
information.

## Customization
You can customize by editing the `config.yml` file in the plugin folder. The default config is as follows:
```yaml
# How often the plugin will send packets to players, in ticks.
updateInterval: 2
# Only when player's saturation difference is greater than this value, the plugin will send a packet to the player.
saturationUpdateThreshold: 0.01
# Only when player's exhaustion difference is greater than this value, the plugin will send a packet to the player.
exhaustionUpdateThreshold: 0.01
```
