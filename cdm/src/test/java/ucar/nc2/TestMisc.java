/*
 * Copyright 1998-2009 University Corporation for Atmospheric Research/Unidata
 *
 * Portions of this software were developed by the Unidata Program at the
 * University Corporation for Atmospheric Research.
 *
 * Access and use of this software shall impose the following obligations
 * and understandings on the user. The user is granted the right, without
 * any fee or cost, to use, copy, modify, alter, enhance and distribute
 * this software, and any derivative works thereof, and its supporting
 * documentation for any purpose whatsoever, provided that this entire
 * notice appears in all copies of the software, derivative works and
 * supporting documentation.  Further, UCAR requests that the user credit
 * UCAR/Unidata in any publications that result from the use of this
 * software or in any product that includes this software. The names UCAR
 * and/or Unidata, however, may not be used in any advertising or publicity
 * to endorse or promote any products or commercial entity unless specific
 * written permission is obtained from UCAR/Unidata. The user also
 * understands that UCAR/Unidata is not obligated to provide the user with
 * any support, consulting, training or assistance of any kind with regard
 * to the use, operation and performance of this software nor to provide
 * the user with any updates, revisions, new versions or "bug fixes."
 *
 * THIS SOFTWARE IS PROVIDED BY UCAR/UNIDATA "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL UCAR/UNIDATA BE LIABLE FOR ANY SPECIAL,
 * INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING
 * FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
 * NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION
 * WITH THE ACCESS, USE OR PERFORMANCE OF THIS SOFTWARE.
 */
package ucar.nc2;

import junit.framework.TestCase;
import ucar.nc2.util.EscapeStrings;
import ucar.ma2.*;
import ucar.nc2.dt.grid.GridDataset;
import ucar.nc2.dt.grid.GeoGrid;
import ucar.nc2.dt.GridCoordSystem;
import ucar.unidata.geoloc.vertical.VerticalTransform;
import ucar.unidata.io.RandomAccessFile;

import java.io.IOException;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * misc testing
 *
 * @author caron
 * @since May 13, 2009
 */
public class TestMisc extends TestCase {

  public void testBackslashTokens() {
    testBackslashTokens("group/name.member.mom");
    testBackslashTokens("var\\.name.member.mom");
    testBackslashTokens("var\\.name.member.mom\\");
    testBackslashTokens("var\\.name.member.mom.");
    testBackslashTokens(".var\\.name.member.mom.");
    testBackslashTokens("...mom.");
  }

  private void testBackslashTokens(String escapedName) {
    System.out.printf("%s%n", escapedName);
    List<String> result = EscapeStrings.tokenizeEscapedName(escapedName);
    for (String r : result)
      System.out.printf("   %s%n", r);
    System.out.printf("%n");
  }

  public void testCompareLongs() {

    try {

      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
      Date[] dateList = new Date[]{
              sdf.parse("2002-01-01"),
              sdf.parse("2002-01-02"),
              sdf.parse("2002-01-03"),
              sdf.parse("2002-01-04"),
              sdf.parse("2002-02-05"),
              sdf.parse("2002-03-06")
      };

      Arrays.sort(dateList, new DateComparator1());

      System.out.println("sort error: " + Arrays.toString(dateList));

      Arrays.sort(dateList, new DateComparator2());

      System.out.println("sort fix:   " + Arrays.toString(dateList));


    } catch (Exception e) {
      e.printStackTrace();
    }

    Long.toString(0);

  }


  // reverse sort - latest come first
  private class DateComparator1 implements Comparator<Date> {
    public int compare(Date f1, Date f2) {
      System.out.print(f2 + "-" + f1 + " =" + f2.getTime() + "-" + f1.getTime() + " =  int: " + (int) (f2.getTime() - f1.getTime()));
      System.out.println("  long: " + (f2.getTime() - f1.getTime()));

      return (int) (f2.getTime() - f1.getTime());
    }
  }

  // reverse sort - latest come first
  private class DateComparator2 implements Comparator<Date> {
    public int compare(Date f1, Date f2) {
      // return (thisVal<anotherVal ? -1 : (thisVal==anotherVal ? 0 : 1));

      if (f2.getTime() == f1.getTime()) return 0;
      return (f2.getTime() > f1.getTime()) ? 1 : -1;
    }
  }

  public static void main(String[] args) {
    String s1 = "CoastWatch/MODSCW/closest_chlora/Mean/CB05/P2009190";
    String s2 = "CoastWatch/MODSCW/closest_chlora/Mean/SE05/P2009190";
    System.out.printf("s1 = %d s2 = %d%n", s1.hashCode(), s2.hashCode());

  }

}
