# GIMO-m

GIMO-m is a generic multi-class data mining system based on the ideas from the original (application-specific and binary) GIMO system as published
in https://arxiv.org/abs/1812.09746 (Tobias Baum, Steffen Herbold, Kurt Schneider: "A Multi-Objective Anytime Rule Mining System to Ease Iterative Feedback from Domain Experts").
Like GIMO, GIMO-m differs from most other data mining algorithms because it is interactive and multi-objective.

You can start GIMO-m with a CSV file as the only command line argument. There needs to be a column named "classification" that contains
the class labels. The type of all other columns (numeric or string) is automatically determined. Once GIMO-m is started, you can open it
in a browser (usually at localhost:4567) and start to interact with it. Usually, you want to start a "Mining agent" that will automatically
determine classification rules.

The repository contains two example CSV files (testdata1.csv and testdata2.csv) that you can use to play around a bit.
