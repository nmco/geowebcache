.. _configuration.layers.parameterfilters:

Styles Legends
==============

Styles legends information can be provided through XML configuration or will be automatically parsed from a WMS capabilities document when using this type of configuration. The styles legends information will be used when producing capabilities documents for WMTS and WMS services to build legend urls for layers styles.

A legend configuration is associated with a certain style in the context of a certain layer, follows a configuration example:

.. code-block:: xml

   <wmsLayer>
      <name>topp:states</name>
      <parameterFilters>
        <stringParameterFilter>
          <key>STYLES</key>
          <defaultValue>population</defaultValue>
          <values>
            <string>population</string>
            <string>polygon</string>
            <string>pophatch</string>
          </values>
        </stringParameterFilter>
      </parameterFilters>
      <wmsUrl>
        <string>http://localhost:8080/geoserver/topp/wms</string>
      </wmsUrl>
      <legends defaultWidth="20" defaultHeight="20" defaultFormat="image/png">
        <legend style="population"/>
        <legend style="pophatch">
          <format>image/jpeg</format>
          <url>http://localhost:8020/geowebcache/wms</url>
        </legend>
        <legend style="polygon">
          <width>50</width>
          <height>100</height>
          <format>image/gif</format>
          <completeUrl>http://localhost/polygon.gif</completeUrl>
        </legend>
      </legends>
    </wmsLayer>


The ``<legends>`` element contains the layer styles legends configurations and allow us to provide default values for width, height and format. If default values are configured, in is simple form a legend configuration only needs to provide the name of the associated style. For example the legend configured for ``population`` style will produce the following legend url:

.. code-block:: xml

  <LegendURL width="20" height="20" format="image/png" 
    xlink:href="http://localhost:8080/geoserver/topp/wms?service=WMS&amp;request=GetLegendGraphic&amp;format=image/png&amp;width=20&amp;height=20&amp;layer=topp%3Astates&amp;style=population"/>

Note that the layer base url was used as the base url for the legend url. In the example above a different base url is provided for the legend associated with style ``pophatch`` using the ``<url>`` element. The produced legend url will look like this:

.. code-block:: xml

  <LegendURL width="20" height="20" format="image/png" 
    xlink:href="http://localhost:8020/geowebcache/wms?service=WMS&amp;request=GetLegendGraphic&amp;format=image/jpeg&amp;width=20&amp;height=20&amp;layer=topp%3Astates&amp;style=pophatch"/>

In some situations it may be useful to provide an already complete url to the legend image (custom vendors parameters, a static image or different protocol). In the example above the legend url for style ``polygon`` will use an already complete url and will look like this:

.. code-block:: xml

  <LegendURL width="50" height="100" format="image/gif" xlink:href="http://localhost/polygon.gif"/>

When building a legend url for a certain style if is not possible to obtain the width, height, format or the name of the associated style an exception will be throw.