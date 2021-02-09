package de.tudarmstadt.ukp.inception.htmleditor.progress;

import de.tudarmstadt.ukp.clarin.webanno.model.Tag;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

import java.util.HashSet;


public class ProgressModel implements IModel<Double> {
    private final Model<HashSet<Integer>> taggedPairsModel;
    private final int totalSize;
    public ProgressModel(final Model<HashSet<Integer>> taggedPairsModel, int totalSize){
        super();
        this.taggedPairsModel = taggedPairsModel;
        this.totalSize = totalSize;
    }

    @Override
    public Double getObject() {
        if(totalSize < 1){
            return 0.0;
        }
        return ((double)taggedPairsModel.getObject().size()) / ((double)totalSize);
    }

    @Override
    public void detach() {
        taggedPairsModel.detach();
    }


}
