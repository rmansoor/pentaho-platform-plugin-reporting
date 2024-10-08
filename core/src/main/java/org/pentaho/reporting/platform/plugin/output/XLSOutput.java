/*!
 * This program is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License, version 2.1 as published by the Free Software
 * Foundation.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this
 * program; if not, you can obtain a copy at http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 * or from the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
 */

package org.pentaho.reporting.platform.plugin.output;

import org.pentaho.reporting.engine.classic.core.MasterReport;
import org.pentaho.reporting.engine.classic.core.ReportProcessingException;
import org.pentaho.reporting.engine.classic.core.event.async.IAsyncReportListener;
import org.pentaho.reporting.engine.classic.core.event.async.ReportListenerThreadHolder;
import org.pentaho.reporting.engine.classic.core.modules.output.table.base.FlowReportProcessor;
import org.pentaho.reporting.engine.classic.core.modules.output.table.xls.FlowExcelOutputProcessor;
import org.pentaho.reporting.libraries.base.util.IOUtils;
import org.pentaho.reporting.libraries.repository.ContentIOException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class XLSOutput implements ReportOutputHandler {
  private byte[] templateData;
  private ProxyOutputStream proxyOutputStream;

  public XLSOutput() {
  }

  public void setTemplateDataFromStream( final InputStream templateInputStream ) throws IOException {
    if ( templateInputStream != null ) {
      final ByteArrayOutputStream bout = new ByteArrayOutputStream();
      try {
        IOUtils.getInstance().copyStreams( templateInputStream, bout );
      } finally {
        templateInputStream.close();
      }
      templateData = bout.toByteArray();
    } else {
      templateData = null;
    }
  }

  public byte[] getTemplateData() {
    return templateData;
  }

  public void setTemplateData( final byte[] templateData ) {
    this.templateData = templateData;
  }

  public Object getReportLock() {
    return this;
  }

  private FlowReportProcessor createProcessor( final MasterReport report, final int yieldRate )
    throws ReportProcessingException {
    proxyOutputStream = new ProxyOutputStream();
    final FlowExcelOutputProcessor target =
      new FlowExcelOutputProcessor( report.getConfiguration(), proxyOutputStream, report.getResourceManager() );
    target.setUseXlsxFormat( false );
    final FlowReportProcessor reportProcessor = new FlowReportProcessor( report, target );

    if ( yieldRate > 0 ) {
      reportProcessor.addReportProgressListener( getYieldListener( yieldRate ) );
    }
    return reportProcessor;
  }

  public int paginate( MasterReport report, int yieldRate ) throws ReportProcessingException, IOException,
    ContentIOException {
    return 0;
  }

  public int generate( final MasterReport report, final int acceptedPage, final OutputStream outputStream,
                       final int yieldRate ) throws ReportProcessingException, IOException {
    final FlowReportProcessor reportProcessor = createProcessor( report, yieldRate );
    final IAsyncReportListener listener = ReportListenerThreadHolder.getListener();
    //Add async job listener
    if ( listener != null ) {
      reportProcessor.addReportProgressListener( listener );
    }
    try {
      proxyOutputStream.setParent( outputStream );
      if ( templateData != null ) {
        final FlowExcelOutputProcessor target = (FlowExcelOutputProcessor) reportProcessor.getOutputProcessor();
        target.setTemplateInputStream( new ByteArrayInputStream( templateData ) );
      }

      reportProcessor.processReport();
      if ( listener != null ) {
        listener.setIsQueryLimitReached( reportProcessor.isQueryLimitReached() );
      }
      outputStream.flush();
      return 0;
    } finally {
      if ( listener != null ) {
        reportProcessor.removeReportProgressListener( listener );
      }
      reportProcessor.close();
      proxyOutputStream.setParent( null );
      final FlowExcelOutputProcessor target = (FlowExcelOutputProcessor) reportProcessor.getOutputProcessor();
      target.setTemplateInputStream( null );
    }

  }

  public boolean supportsPagination() {
    return false;
  }

  public void close() {
  }
}
