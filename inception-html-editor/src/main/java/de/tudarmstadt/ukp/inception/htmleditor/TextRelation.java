package de.tudarmstadt.ukp.inception.htmleditor;

import de.tudarmstadt.ukp.clarin.webanno.model.Tag;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;

public class TextRelation {
    private String sentence1, sentence2;
    private Tag relationRight, relationLeft;
    public TextRelation(String aSentence1, String aSentence2){
        sentence1 = aSentence1;
        sentence2 = aSentence2;
    }
    public TextRelation(String aSentence1, String aSentence2, Tag aRelationRight, Tag aRelationLeft){
        sentence1 = aSentence1;
        sentence2 = aSentence2;
        relationRight = aRelationRight;
        relationLeft = aRelationLeft;
    }

    @Override
    public String toString() {
        String relRight, relLeft;
        if(relationRight == null){
            relRight = "null";
        }else{
            relRight = relationRight.getName();
        }
        if(relationLeft == null){
            relLeft = "null";
        }else{
            relLeft = relationRight.getName();
        }
        return "TextRelation{" +
            "sentence1=" + sentence1 +
            ", sentence2=" + sentence2 +
            ", relationRight=" + relRight +
            ", relationLeft=" + relLeft +
            '}';
    }

    public void setRelationRight(Tag aRelationRight){
        relationRight = aRelationRight;
    }
    public void setRelationLeft(Tag aRelationLeft){
        relationLeft = aRelationLeft;
    }
    public Tag getRelationRight(){
        return relationRight;
    }
    public Tag getRelationLeft(){
        return relationLeft;
    }
}
