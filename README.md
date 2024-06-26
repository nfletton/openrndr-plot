# OPENRNDR Plot

OPENRNDR Plot is a work in progress to create an addon for 
[OPENRNDR](https://github.com/openrndr/openrndr) that generates
commands to drive the [AxiDraw Python API](https://axidraw.com/doc/py_api/).

The goal is to offer more control and flexibility over the plotter's
operation than can be easily achieved by driving the plotter from an SVG
file. 

Particular use cases where plotting via the API may be beneficial include:

- Using multiple pens of different colors
- Refilling pens after set distance travelled
- Pausing mid-plot and continuing from the pause point
- Using a paintbrush requiring brush dipping and washing

## Outputs
OPENRNDR Plot generates three outputs:
1. A text file containing commands consumed by
   [Plot Director](https://github.com/nfletton/plot-director) to drive the AxiDraw
   plotter via the Python API. Details of the command file are 
   [here](https://github.com/nfletton/plot-director/tree/master?#command-input-file-format)
2. An SVG file of the plot scaled to the paper size being used. Plotting this file
   using Inkscape and the AxiDraw extension should (if OPENRNDR Plot is working 
   correctly!) scribe the exact same path as using the command file method above.
3. An SVG file of the plot area layout including paper, 
   paint well and wash well positions. 
   This file is useful for drawing the outline of these elements 
   before laying them out.

## Usage
See `demos` directory

## Feature Wishlist
- maintain a list of warnings and display them at the top of the output file as comments
- add stages for halting and restarting plots
- pause after distance travelled e.g. for a manual ink pen refill
- provide config for different AxiDraw pen height and speed options for drawing, refilling and stirring
- hatch fill
- handle small circles as dots
