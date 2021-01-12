package de.tudarmstadt.ukp.inception.htmleditor;


import de.tudarmstadt.ukp.clarin.webanno.model.Tag;
import org.apache.wicket.model.IModel;

public class ActiveCSSPropertyModel implements IModel<String> {
    private IModel<Tag> relationModel;
    public ActiveCSSPropertyModel(final IModel<Tag> relationModel){
        super();
        this.relationModel = relationModel;
    }

    @Override
    public String getObject() {
        Tag tag = relationModel.getObject();
        return tag != null ? " active" : "";
    }

    @Override
    public void detach() {
        relationModel.detach();
    }


}
