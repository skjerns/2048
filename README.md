2048
====

This is a port of Gabriele Cirulli's 2048 game to Android, all credit for game design goes to him.

Logo redrawn by Margaret Lu.

[Original code](https://github.com/gabrielecirulli/2048) (github.com)

[Download on Google Play](https://play.google.com/store/apps/details?id=com.tpcstld.twozerogame) (play.google.com)

Use governed by the MIT License.

## About this fork

An ad-free build of the game, installable next to the Play Store version
(`com.tpcstld.twozerogame.noads`), with a few additions:

* **Board sizes:** tap the `4×4` button to play on 4×4, 5×5 or 6×6. Each size keeps its own high score.
* **Undo coins:** the first time in a game that a merge creates a value of 32 or higher, you earn one
  undo coin. Every undo spends a coin and steps back one move, so undos can be chained as long as
  coins last. The badge on the undo button shows how many you have.
* **Undo animation:** an undo plays the move backwards. The new tile shrinks away and merged tiles
  split and slide back to where they came from.
* **Achievement effect:** those first-time merges radiate the tile's colour out across the board,
  more intensely the bigger the value. From 1024 on there is a random cheer, a flash, a screen shake
  and a buzz; 2048 rains confetti over the whole screen; 4096 adds fireworks, rainbow rings and a
  wave through the tiles; 8192 and up add a turning sunburst. Every merge pops harder the bigger
  its value, and repeated merges from 256 on get a small burst.
* **High scores:** the button under the board lists the 10 best games of every board size, one tab
  per size, with the best tile of each game.

Build with JDK 17–21 and the Android SDK: `cd 2048 && ./gradlew :base:assembleDebug`.
`tools/run-emulator.sh` builds, boots an emulator and installs the game in one go.

## Contributing

This is an open source project and PRs are welcome and appreciated. Here are some of the things that you could help out with: 

* Add various language translations