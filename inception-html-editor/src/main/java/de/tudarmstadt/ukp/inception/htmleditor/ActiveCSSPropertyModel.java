package de.tudarmstadt.ukp.inception.htmleditor;


import de.tudarmstadt.ukp.clarin.webanno.model.Tag;
import org.apache.wicket.model.IModel;

public class ActiveCSSPropertyModel implements IModel<String> {
    private final IModel<Tag> relationModel;
    public ActiveCSSPropertyModel(final IModel<Tag> relationModel){
        super();
        this.relationModel = relationModel;
    }

    @Override
    public String getObject() {
        Tag tag = relationModel.getObject();
        // Get Name --> yellow for unset
        return tag != null ? " active" : "";
    }

    @Override
    public void detach() {
        relationModel.detach();
    }


}
