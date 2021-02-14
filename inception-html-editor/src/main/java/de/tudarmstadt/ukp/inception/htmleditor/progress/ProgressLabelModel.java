package de.tudarmstadt.ukp.inception.htmleditor.progress;

import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

import java.util.HashSet;

public class ProgressLabelModel implements IModel<String> {
    private final ProgressModel progress;
    public ProgressLabelModel(final ProgressModel progress){
        super();
        this.progress = progress;
    }

    @Override
    public String getObject() {
        return Math.round(progress.getObject()[0] * 100) + "% - " + progress.getObject()[1] + "/" + progress.getObject()[2];
    }

    @Override
    public void detach() {
        progress.detach();
    }

}
