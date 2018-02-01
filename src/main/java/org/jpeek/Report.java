/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2018 Yegor Bugayenko
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.jpeek;

import com.jcabi.xml.ClasspathSources;
import com.jcabi.xml.Sources;
import com.jcabi.xml.StrictXML;
import com.jcabi.xml.XML;
import com.jcabi.xml.XSD;
import com.jcabi.xml.XSDDocument;
import com.jcabi.xml.XSL;
import com.jcabi.xml.XSLDocument;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.cactoos.io.LengthOf;
import org.cactoos.io.TeeInput;
import org.cactoos.iterable.IterableOf;
import org.cactoos.scalar.IoCheckedScalar;
import org.cactoos.scalar.Reduced;
import org.cactoos.text.TextOf;

/**
 * Single report.
 *
 * <p>There is no thread-safety guarantee.
 *
 * @author Yegor Bugayenko (yegor256@gmail.com)
 * @version $Id$
 * @since 0.1
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
final class Report {
    /**
     * Default mean.
     */
    private static final double DEFAULT_MEAN = 0.5d;
    /**
     * Default sigma.
     */
    private static final double DEFAULT_SIGMA = 0.1d;

    /**
     * XSD schema.
     */
    private static final XSD SCHEMA = XSDDocument.make(
        Report.class.getResourceAsStream("xsd/metric.xsd")
    );

    /**
     * XSL stylesheet.
     */
    private static final XSL STYLESHEET = XSLDocument.make(
        Report.class.getResourceAsStream("xsl/metric.xsl")
    ).with(new ClasspathSources());

    /**
     * The skeleton.
     */
    private final XML skeleton;

    /**
     * The metric.
     */
    private final String metric;

    /**
     * Post processing XSLs.
     */
    private final Iterable<XSL> post;

    /**
     * XSL params.
     */
    private final Map<String, Object> params;

    /**
     * Ctor.
     * @param xml Skeleton
     * @param name Name of the metric
     */
    Report(final XML xml, final String name) {
        this(
            xml, name, new HashMap<>(0),
            Report.DEFAULT_MEAN, Report.DEFAULT_SIGMA
        );
    }

    /**
     * Ctor.
     * @param xml Skeleton
     * @param name Name of metric
     * @param args Params for XSL
     */
    Report(final XML xml, final String name, final Map<String, Object> args) {
        this(
            xml, name, args,
            Report.DEFAULT_MEAN, Report.DEFAULT_SIGMA
        );
    }

    /**
     * Ctor.
     * @param xml Skeleton
     * @param name Name of the metric
     * @param args Params for XSL
     * @param mean Mean
     * @param sigma Sigma
     * @checkstyle ParameterNumberCheck (10 lines)
     */
    Report(final XML xml, final String name,
        final Map<String, Object> args,
        final double mean, final double sigma) {
        this.skeleton = xml;
        this.metric = name;
        this.params = args;
        this.post = new IterableOf<>(
            new XSLDocument(
                Report.class.getResourceAsStream("xsl/metric-post-colors.xsl")
            ).with("low", mean - sigma).with("high", mean + sigma),
            new XSLDocument(
                Report.class.getResourceAsStream("xsl/metric-post-range.xsl")
            ),
            new XSLDocument(
                Report.class.getResourceAsStream("xsl/metric-post-bars.xsl")
            )
        );
    }

    /**
     * Save report.
     * @param target Target dir
     * @throws IOException If fails
     */
    public void save(final Path target) throws IOException {
        final XML xml = new StrictXML(
            new ReportWithStatistics(
                new IoCheckedScalar<>(
                    new Reduced<>(
                        this.xml(),
                        (doc, xsl) -> xsl.transform(doc),
                        this.post
                    )
                ).value()
            ),
            Report.SCHEMA
        );
        new LengthOf(
            new TeeInput(
                xml.toString(),
                target.resolve(
                    String.format("%s.xml", this.metric)
                )
            )
        ).intValue();
        new LengthOf(
            new TeeInput(
                Report.STYLESHEET.transform(xml).toString(),
                target.resolve(
                    String.format("%s.html", this.metric)
                )
            )
        ).intValue();
    }

    /**
     * Make XML.
     * @return XML
     * @throws IOException If fails
     */
    private XML xml() throws IOException {
        final String name = String.format("metrics/%s.xsl", this.metric);
        final URL res = this.getClass().getResource(name);
        if (res == null) {
            throw new IllegalArgumentException(
                String.format("XSL not found: %s", name)
            );
        }
        return new XSLDocument(
            new TextOf(res).asString(),
            Sources.DUMMY,
            this.params
        ).transform(this.skeleton);
    }

}
