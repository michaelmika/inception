package de.tudarmstadt.ukp.inception.htmleditor.progress;

import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

public class ProgressIndicatorCSSModel implements IModel<String> {
    private static final int WIDTH = 200;
    private final ProgressModel progress;
    public ProgressIndicatorCSSModel(final ProgressModel progress){
        super();
        this.progress = progress;
    }
    @Override
    public String getObject() {
        int p = (int) Math.round(progress.getObject()[0] * 100);
        return "width:" + ((p * WIDTH) / 100) + "px";
    }

    @Override
    public void detach() {
        progress.detach();
    }
}
