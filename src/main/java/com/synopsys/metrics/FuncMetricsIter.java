package com.synopsys.metrics;

import org.apache.commons.io.IOUtils;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.zip.GZIPInputStream;

/**
 * Iterates over a FUNCTIONS.metric.xml.gz file and produces each Function metrics object.
 */
public class FuncMetricsIter implements Iterator<FuncMetrics>, Closeable {

  // The parsed file doesn't have a root element, we need to add them for the XML parser to work.
  protected InputStream xmlRootPrefix = IOUtils.toInputStream("<root>", StandardCharsets.UTF_8);
  protected InputStream xmlRootSuffix = IOUtils.toInputStream("</root>", StandardCharsets.UTF_8);

  // This is the location of the input stream.
  protected String _inputFileName;
  // This is the binary stream for the compressed file
  protected InputStream fileStream;
  // This is the uncompressed stream
  protected InputStream gzipStream;
  // This is the XML input stream
  protected InputStream xmlInput;

  // This is the factory object for build XML stream parsers
  protected XMLInputFactory xmlif;
  // This is the XML stream parser
  protected XMLStreamReader xmlsr;

  public FuncMetricsIter(String filename) throws IOException, XMLStreamException {
    _inputFileName = filename;
    init();
  }

  public void init() throws IOException, XMLStreamException {

    if (_inputFileName == null)
      throw new IllegalArgumentException("Input file to parse is not defined.");

    if (!new File(_inputFileName).canRead())
      throw new IllegalArgumentException("Unable to read file '" + _inputFileName + "'");

    // This is the stream for reading the compressed file.
    fileStream = new FileInputStream(_inputFileName);

    // This is the stream for uncompressing the input file
    if (_inputFileName.endsWith(".gz")) {
      gzipStream = new GZIPInputStream(fileStream);
    } else {
      gzipStream = fileStream;
    }

    // This is the stream with the parent XML tag <root>...</root>
    xmlInput = new SequenceInputStream(xmlRootPrefix, new SequenceInputStream(gzipStream, xmlRootSuffix));

    xmlif = XMLInputFactory.newInstance();

    // disable resolving of external DTD entities
    xmlif.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);

    // or disallow DTDs entirely
    xmlif.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);

    if (xmlif.isPropertySupported("javax.xml.stream.isReplacingEntityReferences")) {
      xmlif.setProperty("javax.xml.stream.isReplacingEntityReferences", Boolean.TRUE);
    }

    xmlsr = xmlif.createXMLStreamReader(xmlInput);
  }

  @Override
  public boolean hasNext() {
    boolean result = false;
    try {
      result = (xmlsr != null) && xmlsr.hasNext();
      if (result) {
        result = false;
        while (!result && xmlsr.hasNext()) {
          int eventType = xmlsr.next();
          result = eventType == XMLEvent.START_ELEMENT && "fnmetric".equals(xmlsr.getName().getLocalPart());
        }
      }
    } catch (XMLStreamException e) {
      e.printStackTrace();
      result = false;
    }
    return result;
  }

  @Override
  public FuncMetrics next() {
    FuncMetrics result = new FuncMetrics();
    boolean loaded = result.read(xmlsr);
    while (!loaded && hasNext()) {
      loaded = result.read(xmlsr);
    }

    return loaded ? result : null;
  }

  @Override
  public void close() throws IOException {

    if (xmlsr != null)
      try {
        xmlsr.close();
      } catch (XMLStreamException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }

    if (xmlInput != null)
      xmlInput.close();
  }

}
