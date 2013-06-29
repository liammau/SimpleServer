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
package simpleserver;

public enum Color {
  BLACK('0'),
  DARK_BLUE('1'),
  DARK_GREEN('2'),
  DARK_CYAN('3'),
  DARK_RED('4'),
  PURPLE('5'),
  GOLD('6'),
  GRAY('7'),
  DARK_GRAY('8'),
  BLUE('9'),
  GREEN('a'),
  CYAN('b'),
  RED('c'),
  PINK('d'),
  YELLOW('e'),
  WHITE('f');

  private char code;

  Color(char code) {
    this.code = code;
  }

  @Override
  public String toString() {
    return "\u00a7" + code;
  }

  public String toColorString() {
    // @todo test remaining colors if they work w/ MC
    switch (code) {
      case '0':
        return "black";
      case '1':
        return "dark blue";
      default:
        return "white";
    }
  }
}
