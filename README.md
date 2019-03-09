JEXER HAS MOVED TO GITLAB
=========================

Hello GitHub users.  Jexer's official home is over at GitLab:
https://gitlab.com/klamonte/jexer .



Jexer - Java Text User Interface library
----------------------------------------

This library implements a text-based windowing system loosely
reminiscent of Borland's [Turbo
Vision](http://en.wikipedia.org/wiki/Turbo_Vision) system.  It looks
like this:

![Several Windows Open Including A Terminal](/screenshots/screenshot1.png?raw=true "Several Windows Open Including A Terminal")

Jexer works on both Xterm-like terminals and Swing, and supports
images in both Xterm and Swing.  On Swing, images are true color:

![Swing Snake Image](/screenshots/snake_swing.png?raw=true "Swing Snake Image")

On Xterm, images are dithered to a common palette:

![Xterm Snake Image](/screenshots/snake_xterm.png?raw=true "Xterm Snake Image")



License
-------

Jexer is available to all under the MIT License.  See the file LICENSE
for the full license text.



Obtaining Jexer
---------------

Jexer is available on Maven Central:

```xml
<dependency>
  <groupId>com.gitlab.klamonte</groupId>
  <artifactId>jexer</artifactId>
  <version>0.3.0</version>
</dependency>
```

Binary releases are available on SourceForge:
https://sourceforge.net/projects/jexer/files/jexer/

The Jexer source code is hosted at: https://gitlab.com/klamonte/jexer



Documentation
-------------

* [Java API Docs](https://jexer.sourceforge.io/apidocs/api/index.html)

* [Wiki](https://gitlab.com/klamonte/jexer/wikis/home)



Programming Examples
--------------------

The examples/ folder currently contains:

  * A [prototype tiling window
    manager](/examples/JexerTilingWindowManager.java) in less than 250
    lines of code.

  * A [prototype image thumbnail
    viewer](/examples/JexerImageViewer.java) in less than 350 lines of
    code.

jexer.demos contains official demos showing all of the existing UI
controls.  The demos can be run as follows:

  * 'java -jar jexer.jar' .  This will use System.in/out with
    Xterm-like sequences on non-Windows non-Mac platforms.  On Windows
    and Mac it will use a Swing JFrame.

  * 'java -Djexer.Swing=true -jar jexer.jar' .  This will always use
    Swing on any platform.

  * 'java -cp jexer.jar jexer.demos.Demo2 PORT' (where PORT is a
    number to run the TCP daemon on).  This will use the Xterm backend
    on a telnet server that will update with screen size changes.

  * 'java -cp jexer.jar jexer.demos.Demo3' .  This will use
    System.in/out with Xterm-like sequences.  One can see in the code
    how to pass a different InputReader and OutputReader to
    TApplication, permitting a different encoding than UTF-8.

  * 'java -cp jexer.jar jexer.demos.Demo4' .  This demonstrates hidden
    windows and a custom TDesktop.

  * 'java -cp jexer.jar jexer.demos.Demo5' .  This demonstrates two
    demo applications using different fonts in the same Swing frame.

  * 'java -cp jexer.jar jexer.demos.Demo6' .  This demonstrates two
    applications performing I/O across three screens: an Xterm screen
    and Swing screen, monitored from a third Swing screen.



More Screenshots
----------------

![Yo Dawg...](/screenshots/yodawg.png?raw=true "Yo Dawg, I heard you like text windowing systems, so I ran a text windowing system inside your text windowing system so you can have a terminal in your terminal.")

![Sixel Pictures Of Cliffs Of Moher And Buoy](/screenshots/sixel_images.png?raw=true "Sixel Pictures Of Cliffs Of Moher And Buoy")

![Sixel Color Wheel](/screenshots/sixel_color_wheel.png?raw=true "Sixel Color Wheel")



Terminal Support
----------------

The table below lists terminals tested against Jexer's Xterm backend:

| Terminal       | Environment        | Mouse Click | Mouse Cursor | Images |
| -------------- | ------------------ | ----------- | ------------ | ------ |
| xterm          | X11                | yes         | yes          | yes    |
| lcxterm(3)     | CLI, Linux console | yes         | yes          | no     |
| rxvt-unicode   | X11                | yes         | yes          | no(2)  |
| alacritty(3)   | X11                | yes         | yes          | no     |
| gnome-terminal | X11                | yes         | yes          | no     |
| xfce4-terminal | X11                | yes         | yes          | no     |
| mlterm         | X11                | yes         | yes          | no(5)  |
| aminal(3)      | X11                | yes         | no           | no     |
| konsole        | X11                | yes         | no           | no     |
| yakuake        | X11                | yes         | no           | no     |
| screen         | CLI                | yes(1)      | yes(1)       | no(2)  |
| tmux           | CLI                | yes(1)      | yes(1)       | no     |
| putty          | X11, Windows       | yes         | no           | no(2)  |
| Linux          | Linux console      | no          | no           | no(2)  |
| qodem(3)       | CLI, Linux console | yes         | yes(4)       | no     |
| qodem-x11(3)   | X11                | yes         | no           | no     |

1 - Requires mouse support from host terminal.

2 - Also fails to filter out sixel data, leaving garbage on screen.

3 - Latest in repository.

4 - Requires TERM=xterm-1003 before starting.

5 - Opening image crashes terminal.



See Also
--------

* [Tranquil Java IDE](https://tjide.sourceforge.io) is a TUI-based
  integrated development environment for the Java language that was
  built using a very lightly modified GPL version of Jexer.  TJ
  provided a real-world use case to shake out numerous bugs and
  limitations of Jexer.

* [LCXterm](https://lcxterm.sourceforge.io) is a curses-based terminal
  emulator that allows one to use Jexer with full support on the raw
  Linux console.

* [ptypipe](https://gitlab.com/klamonte/ptypipe) is a small C utility
  that permits a Jexer TTerminalWindow to resize the running shell
  when its window is resized.



Acknowledgements
----------------

Jexer makes use of the Terminus TrueType font [made available
here](http://files.ax86.net/terminus-ttf/) .
