package de.tudarmstadt.ukp.inception.htmleditor.meta;

import org.apache.uima.cas.text.AnnotationFS;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.Model;

import java.util.List;

public class MetaDataPanel extends Panel {
    public MetaDataPanel(String id, List<AnnotationFS> segments, Model<Integer> textSegmentIndex){
        super(id);
        add(new ExternalLink(
            "link",
            new SegmentAnnotationFeatureModel(segments, textSegmentIndex, "url"),
            new SegmentAnnotationFeatureModel(segments, textSegmentIndex, "doc_id")
        ));

    }
}
