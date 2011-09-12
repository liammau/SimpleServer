/*
 * Copyright (c) 2010 SimpleServer authors (see CONTRIBUTORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package simpleserver.config.xml;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import simpleserver.config.AbstractConfig;
import simpleserver.config.xml.legacy.LegacyPermissionConfig;

import com.sun.org.apache.xml.internal.serialize.OutputFormat;
import com.sun.org.apache.xml.internal.serialize.XMLSerializer;

public class GlobalConfig extends AbstractConfig {
  public Config config;
  public static final Lock lock = new ReentrantLock();
  public boolean loadsuccess;

  public GlobalConfig() {
    super("config.xml");
  }

  @Override
  public void load() {
    lock.lock();
    loadsuccess = false;

    Config defaults;
    try {
      defaults = loadDefaults();
    } catch (Exception e) {
      System.out.println("[SimpleServer] Error while loading default config.xml");
      e.printStackTrace();
      lock.unlock();
      return;
    }

    if (!getFile().exists()) {
      Config config;
      if ((config = LegacyPermissionConfig.load()) != null) {
        this.config = config;
        System.out.println("[SimpleServer] Converted permisisons.xml to config.xml");
      } else {
        this.config = defaults;
        System.out.println("[SimpleServer] Loaded default config.xml");
      }
    } else {
      try {
        config = load(new FileInputStream(getFile()));
      } catch (Exception e) {
        System.out.println("[SimpleServer] Error in config.xml: " + e);
        lock.unlock();
        return;
      }
      completeConfig(config, defaults);
    }

    save();
    loadsuccess = true;
    lock.unlock();
  }

  private Config loadDefaults() throws SAXException, IOException {
    return load(getClass().getResourceAsStream(filename));
  }

  private Config load(InputStream stream) throws SAXException, IOException {
    XMLReader xml = XMLReaderFactory.createXMLReader();
    XMLTagResolver handler = new XMLTagResolver();
    xml.setContentHandler(handler);
    xml.setErrorHandler(handler);
    xml.setFeature("http://xml.org/sax/features/validation", true);
    xml.setEntityResolver(handler);
    xml.parse(new InputSource(new InputStreamReader(stream)));

    return handler.root();
  }

  private void completeConfig(Config config, Config defaults) {
    // Properties
    for (Property prop : defaults.properties) {
      if (!config.properties.contains(prop.name)) {
        config.properties.add(prop);
      }
    }

    // Commands
    Set<String> commands = new HashSet<String>();
    for (CommandConfig cmd : config.commands) {
      commands.add(cmd.originalName);
    }
    for (CommandConfig cmd : defaults.commands) {
      if (!commands.contains(cmd.originalName)) {
        config.commands.add(cmd);
      }
    }
  }

  @Override
  public void save() {
    lock.lock();
    FileOutputStream fos;
    try {
      fos = new FileOutputStream(getFile());
    } catch (FileNotFoundException e) {
      e.printStackTrace();
      lock.unlock();
      return;
    }
    OutputFormat of = new OutputFormat("XML", "UTF-8", true);
    of.setIndent(1);
    of.setIndenting(true);
    of.setLineWidth(200);
    of.setDoctype(null, "http://somewhere/config.dtd ");
    XMLSerializer serializer = new ConfigSerializer(fos, of);
    ContentHandler hd;
    try {
      hd = serializer.asContentHandler();

      hd.startDocument();
      config.save(hd, serializer);
      hd.endDocument();
      fos.close();
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      lock.unlock();
    }
  }

  public static final void main(String[] args) {
    getMemory();
    GlobalConfig conf = new GlobalConfig();
    conf.load();
    long start = new Date().getTime();
    conf.load();
    long end = new Date().getTime();
    System.out.println("\nLoading time: " + (end - start) + " ms");
    getMemory();
    start = new Date().getTime();
    conf.save();
    end = new Date().getTime();
    System.out.println("\nSaving time: " + (end - start) + " ms");

    /*
    Area[] areas = new Area[] { new Area("a", new Coordinate(-5, 0, -5), new Coordinate(1, 127, 1)),
                                new Area("b", new Coordinate(1, 0, 1), new Coordinate(1, 0, 1)),
                                new Area("c", new Coordinate(16, 0, 16), new Coordinate(18, 8, 18)),
                                new Area("d", new Coordinate(100, 0, 100), new Coordinate(100, 0, 100))
    };

    for (Area area : areas) {
      Set<Area> overlaps = conf.config.dimensions.get(Dimension.EARTH).areas.overlaps(area);
      System.out.println(area.name);
      for (Area overlap : overlaps) {
        System.out.print(overlap.name + ", ");
      }
      System.out.println();
    }
    */

    System.out.println();
    conf = null;
    // areas = null;
    getMemory();
  }

  private static final void getMemory() {
    Runtime runtime = Runtime.getRuntime();
    runtime.gc();
    long total = runtime.totalMemory() / 1000;
    long free = runtime.freeMemory() / 1000;
    System.out.println(String.format("Total memory: %d MB  free: %d MB  used: %d KB", total / 1000, free / 1000, (total - free)));
  }

  @Override
  protected void loadHeader() {
  }
}