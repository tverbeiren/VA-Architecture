VA-Architecture
===============

Visual Analytics architecture implementation for fast/realtime aggregation of data using D3.js, Neith, Locustree, Spark and Spark Jobserver.

This code accompanies the submission to [LDAV 2014](http://www.ldav.org/).


It is an implementation of 3 layered architecture for interactively visualizing Big Data. The 3 layers are:

* Visualization layer
* Locustree layer for abstracting away query and caching
* Backend, running on a cluster


It uses the following libraries:

* [Neith](https://github.com/mattbierner/neith) for a functional tree zipper
* [D3.js](http://d3js.org/)
* [Spark](http://spark.apache.org/)
* Ooyala [Spark Job Server](https://github.com/ooyala/spark-jobserver)
* And a little bit of [JQuery.js](http://jquery.com/)

Although the architecture consists of 3 layers, the visualization and locustree layer still have to be split.


TODO
====

* Remove dependency on JQuery.
* Add more information on implementation
* Split visualization and locustree layers.
* Add instructions on how to compile and run
* Clean up where possible




