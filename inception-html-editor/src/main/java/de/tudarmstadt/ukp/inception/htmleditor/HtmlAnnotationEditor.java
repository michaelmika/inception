/*
 * Copyright 2017
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.inception.htmleditor;

import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.TypeUtil.getUiLabelText;
import static org.apache.uima.fit.util.CasUtil.getType;
import static org.apache.uima.fit.util.CasUtil.select;
import static org.apache.uima.fit.util.CasUtil.selectCovered;

import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;

import de.tudarmstadt.ukp.inception.htmleditor.filter.FilterPanel;
import de.tudarmstadt.ukp.inception.htmleditor.meta.MetaDataPanel;
import de.tudarmstadt.ukp.inception.htmleditor.model.Pair;
import de.tudarmstadt.ukp.inception.htmleditor.model.RelationFeatureSupplier;
import de.tudarmstadt.ukp.inception.htmleditor.model.TextRelation;
import de.tudarmstadt.ukp.inception.htmleditor.progress.ProgressPanel;
import de.tudarmstadt.ukp.inception.htmleditor.statistic.StatisticPanel;
import org.apache.uima.cas.Feature;
import org.apache.uima.jcas.tcas.Annotation;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.adapter.SpanAdapter;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.layer.LayerSupportRegistry;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil;
import de.tudarmstadt.ukp.clarin.webanno.model.*;
import de.tudarmstadt.ukp.inception.htmleditor.textRelationsAnnotator.TextRelationsCssResourceReference;
import de.tudarmstadt.ukp.inception.htmleditor.textRelationsAnnotator.TextRelationsJavascriptResourceReference;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.page.AnnotationPageBase;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.adapter.RelationAdapter;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASRuntimeException;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.feedback.IFeedback;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;

import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tudarmstadt.ukp.clarin.webanno.api.AnnotationSchemaService;
import de.tudarmstadt.ukp.clarin.webanno.api.CasProvider;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.AnnotationEditorBase;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.AnnotationEditorExtensionRegistry;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.action.AnnotationActionHandler;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.coloring.ColoringService;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.exception.AnnotationException;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.AnnotatorState;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.VID;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.PreRenderer;
import de.tudarmstadt.ukp.clarin.webanno.support.JSONUtil;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;

public class HtmlAnnotationEditor
    extends AnnotationEditorBase
{
    private static final long serialVersionUID = -3358207848681467993L;
    private static final Logger LOG = LoggerFactory.getLogger(HtmlAnnotationEditor.class);

    private @SpringBean PreRenderer preRenderer;
    private @SpringBean AnnotationSchemaService annotationService;
    private @SpringBean AnnotationEditorExtensionRegistry extensionRegistry;
    private @SpringBean ColoringService coloringService;

    // eigene Variablen
    private @SpringBean LayerSupportRegistry layerSupportRegistry;

    private Form<Void> form;
    private Label textLeft, textRight, positionLabel1, positionLabel2;
    private AjaxButton textLeftPrevious, textLeftNext, textRightPrevious, textRightNext;
    private List<AnnotationFS> sentences;
    private Model<String> sentence1, sentence2, positionString1, positionString2;
    private int leftSentenceIndex = 0, rightSentenceIndex = 1;

    public static final String SENTENCE_LAYER_NAME = "webanno.custom.Sentence";
    public static final String RELATION_LAYER_NAME = "webanno.custom.SentenceRelation";
    public static final String EMPTY_FEATURE = "unset";
    private AnnotationLayer sentenceLayer, relationLayer;
    private TagSet tagSetObject;
    private List<Tag> tagList;
    private AnnotationFeature feature;
    private TextRelation relation;

    private List<Pair<Integer, Integer>> possiblePairs;
    private HashSet<Integer> alreadyTaggedPairs = new HashSet<>();
    private int pairIndex = 0;

    private ProgressPanel progressPanel;

    private StatisticPanel statisticPanel;
    private ModalWindow statisticsDialog, filterDialog;

    private CAS cas;

    private boolean preAnnotated = false;

    private static Tag filterLabel = null;
    private FilterPanel filterPanel;

    public Pair<Integer, Integer> getNewPair(String method){
        switch (method) {
            case "next":
                if(pairIndex + 1 < possiblePairs.size()){
                    pairIndex = pairIndex + 1;
                }else{
                    pairIndex = 0;
                }
                break;
            case "previous":
                if(pairIndex - 1 >= 0){
                    pairIndex = pairIndex - 1;
                }else{
                    pairIndex = possiblePairs.size() - 1;
                }
                break;
            default:
                break;
        }
        // Filtering
        if(filterLabel != null){
            // Get Relation Annotation
            int a = possiblePairs.get(pairIndex).getLeft();
            int b = possiblePairs.get(pairIndex).getRight();
            List<Annotation> selectedAnnoList_Left = cas.<Annotation>select(getType(cas,RELATION_LAYER_NAME))
                .coveredBy(sentences.get(a)).asList();
            List<Annotation> selectedAnnoList_Right = cas.<Annotation>select(getType(cas,RELATION_LAYER_NAME))
                .coveredBy(sentences.get(b)).asList();
            //Get Tags
            Tag[] result1 = getRelationTags(selectedAnnoList_Left, sentences.get(a), sentences.get(b));
            Tag[] result2 = getRelationTags(selectedAnnoList_Right, sentences.get(a), sentences.get(b));
            Tag leftRelation = (
                result1[0] != null ? result1[0] :
                    result2[0] != null ? result2[0] : null
            );
            Tag rightRelation = (
                result1[1] != null ? result1[1] :
                    result2[1] != null ? result2[1] : null
            );
            // Check if Tags are equal to filter (or at end of list --> infinite loop)
            if(filterLabel.equals(leftRelation) || filterLabel.equals(rightRelation) || pairIndex + 1 == possiblePairs.size()){
                return possiblePairs.get(pairIndex);
            }else{
                // if not, proceed recursive
                return getNewPair(method);
            }
        }
        return possiblePairs.get(pairIndex);
    }

    public Set<Pair<Integer, Integer>> getPossiblePairItems_efficient()
    {
        Set<Pair<Integer, Integer>> result = new LinkedHashSet<>();
        List<Annotation> annoList = cas.<Annotation>select(getType(cas,RELATION_LAYER_NAME)).asList();
        // Bugfix, if double annotation exists
        int last_i = -1, last_b = -1;
        for (Annotation relation : annoList) {
            // Dependent
            Feature dependentFeat = relation.getType().getFeatureByBaseName("Dependent");
            AnnotationFS dependentFS = (AnnotationFS) relation.getFeatureValue(dependentFeat);
            // Governor
            Feature governorFeat = relation.getType().getFeatureByBaseName("Governor");
            AnnotationFS governorFS = (AnnotationFS) relation.getFeatureValue(governorFeat);
            int i = sentences.indexOf(dependentFS);
            int b = sentences.indexOf(governorFS);
            Pair<Integer, Integer> pair = new Pair<>(i, b);
            boolean added = result.add(pair);
            // if not added check if
            // Delete unset
            // Check if already Tagged
            Feature label1 = relation.getType().getFeatureByBaseName("label");
            String label_string1 = relation.getFeatureValueAsString(label1);
            if(
                !label_string1.equals("")
                && !label_string1.equals(EMPTY_FEATURE)
                && added
            ){
                alreadyTaggedPairs.add(result.size() - 1);
            }else if(
                !label_string1.equals("")
                    && !label_string1.equals(EMPTY_FEATURE)
                    && !added && !alreadyTaggedPairs.contains(result.size() - 1)
                    && last_b == b && last_i == i
            ){
                // Bugfix for double annotation (unset + real annotation; in that order)
                alreadyTaggedPairs.add(result.size() - 1);
                //LOG.info("PAIR NOT FOUND: added: " + added + " index: " + (result.size() - 1) + " label:" + label_string1 + " pair: " + pair.toString());
            }
            last_b = b;
            last_i = i;
        }
        LOG.info("ALL possible Pairs:");
        LOG.info(result.toString());
        LOG.info("Already Tagged:");
        LOG.info(alreadyTaggedPairs.toString());
        return result;
    }

    // Inefficient - not used
    public Set<Pair<Integer, Integer>> getPossiblePairItems()
    {
        Set<Pair<Integer, Integer>> result = new LinkedHashSet<>();
        LOG.info("PAIRS:");
        for (int i = 0; i < sentences.size(); i++){
            Set<Integer> pairItems = getPossiblePairItems(i);
            Iterator<Integer> itr = pairItems.iterator();
            while(itr.hasNext()){
                int b = itr.next();
                if(i != b){
                    Pair<Integer, Integer> relation = new Pair<>(i, b);
                    result.add(relation);
                    // Check if already annotated
                    AnnotationFS a1 = getRelationAnnotation(sentences.get(i), sentences.get(b));
                    AnnotationFS a2 = getRelationAnnotation(sentences.get(b), sentences.get(i));
                    if(a1 != null && a2 != null){
                        Feature label1 = a1.getType().getFeatureByBaseName("label");
                        Feature label2 = a2.getType().getFeatureByBaseName("label");
                        String label_string1 = a1.getFeatureValueAsString(label1);
                        String label_string2 = a1.getFeatureValueAsString(label2);
                        if(
                            !label_string1.equals("") && !label_string1.equals(EMPTY_FEATURE)
                            && !label_string2.equals("") && !label_string2.equals(EMPTY_FEATURE)
                        ){
                            alreadyTaggedPairs.add(result.size() - 1);
                        }
                    }
                }
            }
        }
        LOG.info("getPossiblePairItems");
        LOG.info(result.toString());
        return result;
    }

    public Set<Integer> getPossiblePairItems_efficient(int index)
    {
        Set<Integer> result = new LinkedHashSet<>();
        result.add(index);
        possiblePairs.forEach(
            integerIntegerPair -> {
                if (
                    integerIntegerPair.getLeft() == index
                ) {
                    result.add(integerIntegerPair.getRight());
                }else if (
                    integerIntegerPair.getRight() == index
                ) {
                    result.add(integerIntegerPair.getLeft());
                }
            }
        );
        return result;
    }

    public Set<Integer> getPossiblePairItems(int index)
    {
        Set<Integer> result = new LinkedHashSet<>();
        if(preAnnotated){
            // Only segments that have a connection
            result.add(index);
            for (int i = 0; i < sentences.size(); i++){
                if(
                    getRelationAnnotation(sentences.get(i), sentences.get(index)) != null
                    || getRelationAnnotation(sentences.get(index), sentences.get(i)) != null
                ) {
                    result.add(i);
                }
            }
        }else{
            // All segments
            for (int i = 0; i < sentences.size(); i++){
                result.add(i);
            }
        }
        LOG.info("getPossiblePairItems(index)");
        LOG.info(result.toString());
        return result;
    }

    // get new Sentence index for segment navigation
    public int getNewSentenceIndex(int index1, int index2, String method)
    {
        List<Integer> possibleRelations = new ArrayList<>();
        possibleRelations.addAll(getPossiblePairItems_efficient(index2));
        int tmp = 0;
        if(possibleRelations.size() < 2){
            return possibleRelations.get(tmp);
        }
        int realIndex1 = possibleRelations.indexOf(index1);
        int realIndex2 = possibleRelations.indexOf(index2);
        switch (method){
            case "next":
                LOG.info("Method next");
                if (realIndex1 + 1 > possibleRelations.size() - 1) {
                    tmp = 0;
                }else{
                    tmp = realIndex1 + 1;
                }
                if (tmp == realIndex2) {
                    if(tmp + 1 > possibleRelations.size() - 1){
                        tmp = 0;
                    }else{
                        tmp = tmp + 1;
                    }
                }
                break;
            case "previous":
                LOG.info("Method previous");
                if (realIndex1 - 1 < 0) {
                    tmp = possibleRelations.size() - 1;
                }else{
                    tmp = realIndex1 - 1;
                }
                if (tmp == realIndex2) {
                    if (tmp - 1 < 0) {
                        tmp = possibleRelations.size() - 1;
                    }else{
                        tmp = realIndex1 - 1;
                    }
                }
                break;
            default:
                break;
        }
        return possibleRelations.get(tmp);
    }


    public boolean isSameSentence(AnnotationFS anno1, AnnotationFS anno2)
    {
        return anno1.getBegin() == anno2.getBegin() && anno1.getEnd() == anno2.getEnd();
    }

    public HtmlAnnotationEditor(String aId, IModel<AnnotatorState> aModel,
            AnnotationActionHandler aActionHandler, CasProvider aCasProvider)
    {
        super(aId, aModel, aActionHandler, aCasProvider);
        try{
            cas = aCasProvider.get();
        }catch (IOException e){
            LOG.error(e.toString());
            handleError("Unable to load CAS ", e);
        }
        getDocAttributes();
        getSentences();
        getLayersAndTags();
        possiblePairs = new ArrayList<>();
        possiblePairs.addAll(getPossiblePairItems_efficient());
        Pair<Integer, Integer> firstRelation = possiblePairs.get(pairIndex);
        leftSentenceIndex = firstRelation.getLeft();
        rightSentenceIndex = firstRelation.getRight();
        renderTextRelations();
    }
    public String getSegmentPositionString(int index)
    {
        String result = "";
        if(preAnnotated){
            List<Integer> possibleRelations = new ArrayList<>();
            possibleRelations.addAll(getPossiblePairItems_efficient(index));
            int i = possibleRelations.indexOf(index);
            result = "(" + (i + 1) + "/" + (possibleRelations.size() - 1) + ")";
        }else{
            // Return String
            result = "(" + (index + 1) + "/" + sentences.size() + ")";
        }
        return result;
    }
    public List<AnnotationFS> getAllRelationsFromTargetSegment(AnnotationFS segment)
    {
        List<AnnotationFS> result = new ArrayList<AnnotationFS>();

        return result;
    }
    public Form createForm(){
        Form<Void> form = new Form<Void>("textRelationForm");
        // Add Meta Data Information
        Model leftIndexModel = new Model<Integer>(leftSentenceIndex);
        Model rightIndexModel = new Model<Integer>(rightSentenceIndex);
        if(preAnnotated){
            LOG.info("Sentences: ");
            LOG.info(sentences.toString());
            MetaDataPanel metaDataPanel_left = new MetaDataPanel("metaData_left", sentences, leftIndexModel);
            MetaDataPanel metaDataPanel_right = new MetaDataPanel("metaData_right", sentences, rightIndexModel);
            form.add(metaDataPanel_left);
            form.add(metaDataPanel_right);
        }else{
            form.add(new WebMarkupContainer("metaData_left"));
            form.add(new WebMarkupContainer("metaData_right"));
        }
        // Progress Panel
        progressPanel = new ProgressPanel("progress-panel", alreadyTaggedPairs, possiblePairs);
        form.add(progressPanel);
        // Save Button
        form.add(new AjaxButton("saveButton"){
            @Override
            protected void onSubmit(AjaxRequestTarget target){
                super.onSubmit(target);
                saveCas();
            }
        });
        // Statistic Dialog
        statisticsDialog = new ModalWindow("statistic-panel");
        statisticsDialog.setTitle("Label Statistics");
        int[] valueArray = getTagValues();
        int[] values = Arrays.copyOfRange(valueArray, 0, valueArray.length - 1);
        statisticPanel = new StatisticPanel(statisticsDialog.getContentId(), getTagNames(), values, valueArray[valueArray.length - 1]);
        statisticsDialog.setContent(statisticPanel);
        form.add(statisticsDialog);
        // Statistics Button
        form.add(new AjaxButton("statisticsButton"){
            @Override
            protected void onSubmit(AjaxRequestTarget target){
                super.onSubmit(target);
                int[] valueArray = getTagValues();
                int[] values = Arrays.copyOfRange(valueArray, 0, valueArray.length - 1);
                statisticPanel.setNewValues(values, valueArray[valueArray.length - 1]);
                statisticsDialog.show(target);
            }
        });
        // Filter Dialog
        filterDialog = new ModalWindow("filter-panel");
        filterDialog.setTitle("Filter Label");
        filterPanel = new FilterPanel(filterDialog.getContentId(), tagList, filterDialog);
        filterDialog.setContent(filterPanel);
        form.add(filterDialog);
        // Filter Button
        form.add(new AjaxButton("filterButton"){
            @Override
            protected void onSubmit(AjaxRequestTarget target){
                super.onSubmit(target);
                filterPanel.setTagList(tagList);
                filterDialog.show(target);
            }
        });
        // Navigation Buttons
        form.add(new AjaxButton("pairPrevious"){
            @Override
            protected void onSubmit(AjaxRequestTarget target){
                super.onSubmit(target);
                Pair<Integer, Integer> result = getNewPair("previous");
                leftSentenceIndex = (int) result.getLeft();
                rightSentenceIndex = (int) result.getRight();
                leftIndexModel.setObject(leftSentenceIndex);
                rightIndexModel.setObject(rightSentenceIndex);
                sentence1.setObject(sentences.get(leftSentenceIndex).getCoveredText());
                sentence2.setObject(sentences.get(rightSentenceIndex).getCoveredText());
                positionString1.setObject(getSegmentPositionString(rightSentenceIndex));
                positionString2.setObject(getSegmentPositionString(leftSentenceIndex));
                renderTextRelations(target);
            }
        });
        form.add(new AjaxButton("pairNext"){
            @Override
            protected void onSubmit(AjaxRequestTarget target){
                super.onSubmit(target);
                Pair<Integer, Integer> result = getNewPair("next");
                leftSentenceIndex = (int) result.getLeft();
                rightSentenceIndex = (int) result.getRight();
                leftIndexModel.setObject(leftSentenceIndex);
                rightIndexModel.setObject(rightSentenceIndex);
                sentence1.setObject(sentences.get(leftSentenceIndex).getCoveredText());
                sentence2.setObject(sentences.get(rightSentenceIndex).getCoveredText());
                positionString1.setObject(getSegmentPositionString(rightSentenceIndex));
                positionString2.setObject(getSegmentPositionString(leftSentenceIndex));
                renderTextRelations(target);
            }
        });
        form.add(new AjaxButton("pairNextNone"){
            @Override
            protected void onSubmit(AjaxRequestTarget target){
                super.onSubmit(target);
                // Check if there are annotations left
                if(alreadyTaggedPairs.size() < possiblePairs.size()){
                    Pair<Integer, Integer> result = getNewPair("next");
                    while(alreadyTaggedPairs.contains(pairIndex)){
                        result = getNewPair("next");
                    }
                    LOG.info(alreadyTaggedPairs.toString());
                    LOG.info("Pair Index: " + pairIndex);
                    leftSentenceIndex = (int) result.getLeft();
                    rightSentenceIndex = (int) result.getRight();
                    leftIndexModel.setObject(leftSentenceIndex);
                    rightIndexModel.setObject(rightSentenceIndex);
                    sentence1.setObject(sentences.get(leftSentenceIndex).getCoveredText());
                    sentence2.setObject(sentences.get(rightSentenceIndex).getCoveredText());
                    positionString1.setObject(getSegmentPositionString(rightSentenceIndex));
                    positionString2.setObject(getSegmentPositionString(leftSentenceIndex));
                    renderTextRelations(target);
                }
            }
        });
        form.add(new AjaxButton("textLeftPrevious"){
            @Override
            protected void onSubmit(AjaxRequestTarget target){
                super.onSubmit(target);
                leftSentenceIndex = getNewSentenceIndex(leftSentenceIndex, rightSentenceIndex, "previous");
                leftIndexModel.setObject(leftSentenceIndex);
                sentence1.setObject(sentences.get(leftSentenceIndex).getCoveredText());
                positionString1.setObject(getSegmentPositionString(rightSentenceIndex));
                positionString2.setObject(getSegmentPositionString(leftSentenceIndex));
                // Set pairIndex (for Progress)
                pairIndex = possiblePairs.indexOf(new Pair<>(leftSentenceIndex, rightSentenceIndex));
                LOG.info("FROM INDICES: " + leftSentenceIndex + " , " + rightSentenceIndex);
                LOG.info("NEW INDEX: " + pairIndex);
                renderTextRelations(target);

            }
        });
        form.add(new AjaxButton("textLeftNext"){
            @Override
            protected void onSubmit(AjaxRequestTarget target){
                super.onSubmit(target);
                leftSentenceIndex = getNewSentenceIndex(leftSentenceIndex, rightSentenceIndex, "next");
                leftIndexModel.setObject(leftSentenceIndex);
                sentence1.setObject(sentences.get(leftSentenceIndex).getCoveredText());
                positionString1.setObject(getSegmentPositionString(rightSentenceIndex));
                positionString2.setObject(getSegmentPositionString(leftSentenceIndex));
                // Set pairIndex (for Progress)
                pairIndex = possiblePairs.indexOf(new Pair<>(leftSentenceIndex, rightSentenceIndex));
                LOG.info("FROM INDICES: " + leftSentenceIndex + " , " + rightSentenceIndex);
                LOG.info("NEW INDEX: " + pairIndex);
                renderTextRelations(target);
            }
        });
        form.add(new AjaxButton("textRightPrevious"){
            @Override
            protected void onSubmit(AjaxRequestTarget target){
                super.onSubmit(target);
                rightSentenceIndex = getNewSentenceIndex(rightSentenceIndex, leftSentenceIndex, "previous");
                rightIndexModel.setObject(rightSentenceIndex);
                sentence2.setObject(sentences.get(rightSentenceIndex).getCoveredText());
                positionString1.setObject(getSegmentPositionString(rightSentenceIndex));
                positionString2.setObject(getSegmentPositionString(leftSentenceIndex));
                // Set pairIndex (for Progress)
                pairIndex = possiblePairs.indexOf(new Pair<>(leftSentenceIndex, rightSentenceIndex));
                LOG.info("FROM INDICES: " + leftSentenceIndex + " , " + rightSentenceIndex);
                LOG.info("NEW INDEX: " + pairIndex);
                renderTextRelations(target);
            }
        });
        form.add(new AjaxButton("textRightNext"){
            @Override
            protected void onSubmit(AjaxRequestTarget target){
                super.onSubmit(target);
                rightSentenceIndex = getNewSentenceIndex(rightSentenceIndex, leftSentenceIndex, "next");
                rightIndexModel.setObject(rightSentenceIndex);
                sentence2.setObject(sentences.get(rightSentenceIndex).getCoveredText());
                positionString1.setObject(getSegmentPositionString(rightSentenceIndex));
                positionString2.setObject(getSegmentPositionString(leftSentenceIndex));
                // Set pairIndex (for Progress)
                pairIndex = possiblePairs.indexOf(new Pair<>(leftSentenceIndex, rightSentenceIndex));
                renderTextRelations(target);
            }
        });

        if(sentences.size() < 2){
            // Error
            LOG.error("Not enough sentences");
            // Sentence Left
            textLeft = new Label("textLeft", "Dies ist ein linker Text");
            positionLabel1 = new Label("textLeftPosition", "Position");
            // Sentence Right
            textRight = new Label("textRight", "Dies ist ein rechter Text");
            positionLabel2 = new Label("textRightPosition", "Position");
        }else{
            // Sentence Left
            sentence1 = Model.of(sentences.get(leftSentenceIndex).getCoveredText());
            positionString1 = Model.of(getSegmentPositionString(rightSentenceIndex));
            textLeft = new Label("textLeft", sentence1);
            positionLabel1 = new Label("textLeftPosition", positionString1);
            positionLabel1.setOutputMarkupId(true);
            textLeft.setOutputMarkupId(true);
            // Sentence Right
            sentence2 = Model.of(sentences.get(rightSentenceIndex).getCoveredText());
            positionString2 = Model.of(getSegmentPositionString(leftSentenceIndex));
            textRight = new Label("textRight", sentence2);
            positionLabel2 = new Label("textRightPosition", positionString2);
            positionLabel2.setOutputMarkupId(true);
            textRight.setOutputMarkupId(true);

            // Relations
            relation = new TextRelation(sentence1.getObject(), sentence2.getObject());
            // Relation Models
            PropertyModel<Tag> rightRelModel = new PropertyModel<Tag>(relation, "relationRight");
            PropertyModel<Tag> leftRelModel = new PropertyModel<Tag>(relation, "relationLeft");
            ActiveCSSPropertyModel rightCSSModel = new ActiveCSSPropertyModel(rightRelModel);
            ActiveCSSPropertyModel leftCSSModel = new ActiveCSSPropertyModel(leftRelModel);
            // Colorize Arrows
            WebMarkupContainer rightArrowMarkup = new WebMarkupContainer("relationRightArrow");
            rightArrowMarkup.add(new AttributeAppender("class", rightCSSModel));
            WebMarkupContainer leftArrowMarkup = new WebMarkupContainer("relationLeftArrow");
            leftArrowMarkup.add(new AttributeAppender("class", leftCSSModel));
            form.add(rightArrowMarkup);
            form.add(leftArrowMarkup);

            // DropDown for Right Relation
            form.add(new DropDownChoice<Tag>(
                "relationRight",
                rightRelModel,
                new LoadableDetachableModel<List<Tag>>() {
                    @Override
                    protected List<Tag> load() {
                        return tagList;
                    }
                }
            ).add(new AjaxFormComponentUpdatingBehavior("change") {
                /**
                 * Called when a option is selected of a dropdown list.
                 */
                protected void onUpdate(AjaxRequestTarget aTarget) {
                    Tag tag = (Tag) getFormComponent().getModelObject();
                    relation.setRelationRight(tag);
                    LOG.info("Relation Right Choice: " + tag.getName());
                    // Annotate both active Sentences
                    createSentenceAnnotation();
                    // Annotate relation
                    createRelationAnnotation(tag, leftSentenceIndex, rightSentenceIndex);
                    renderTextRelations(aTarget);
                }
            }));
            // DropDown for Left Relation
            form.add(new DropDownChoice<Tag>(
                "relationLeft",
                leftRelModel,
                new LoadableDetachableModel<List<Tag>>() {
                    @Override
                    protected List<Tag> load() {
                        return tagList;
                    }
                }
            ).add(new AjaxFormComponentUpdatingBehavior("change") {
                /**
                 * Called when a option is selected of a dropdown list.
                 */
                protected void onUpdate(AjaxRequestTarget aTarget) {
                    //LOG.info("TIME PASSED");
                    long start = System.nanoTime();
                    Tag tag = (Tag) getFormComponent().getModelObject();
                    //LOG.info("1: " + (System.nanoTime() - start));
                    relation.setRelationLeft(tag);
                    //LOG.info("Relation Left Choice: " + tag.getName());
                    //LOG.info("2: " + (System.nanoTime() - start));
                    // Annotate both active Sentences
                    createSentenceAnnotation();
                    //LOG.info("3: " + (System.nanoTime() - start));
                    // Annotate relation
                    createRelationAnnotation(tag, rightSentenceIndex, leftSentenceIndex);
                    //LOG.info("4: " + (System.nanoTime() - start));
                    renderTextRelations(aTarget);
                    //LOG.info("5: " + (System.nanoTime() - start));
                    //LOG.info("END TIME PASSED");
                }
            }));

        }
        form.add(positionLabel1);
        form.add(positionLabel2);
        form.add(textLeft);
        form.add(textRight);



        return form;
    }
    public void renderTextRelations()
    {
        form = createForm();
        updateSentenceRelation();
        this.add(form);
    }
    public void renderTextRelations(AjaxRequestTarget aTarget)
    {
        updateSentenceRelation();
        aTarget.add(form);
    }

    @Override
    public void renderHead(IHeaderResponse aResponse)
    {
        super.renderHead(aResponse);

        aResponse.render(CssHeaderItem.forReference(TextRelationsCssResourceReference.get()));
        aResponse.render(
            JavaScriptHeaderItem.forReference(TextRelationsJavascriptResourceReference.get()));
        if (getModelObject().getDocument() != null) {
            //aResponse.render(
            //        OnDomReadyHeaderItem.forScript(initAnnotatorJs(vis, storeAdapter)));
        }
    }
    // For statistic Panel: Calc Numbers of Tag
    public int[] getNumberOfRelationAnnotationsByTag(String aLabel)
    {
        int result[] = new int[2];
        // Number with Tag
        result[0] = 0;
        // Total Number
        result[1] = 0;
        List<Annotation> annos = cas.<Annotation>select(getType(cas,RELATION_LAYER_NAME)).asList();
        for (Annotation anno : annos) {
            Feature label = anno.getType().getFeatureByBaseName("label");
            String labelString = anno.getFeatureValueAsString(label);
            if(!labelString.equals("unset")){
                result[1]++;
                if(labelString.equals(aLabel)){
                    result[0]++;
                }
            }
        }
        return result;
    }
    // get Statistic Data
    public String[] getTagNames()
    {
        String[] names = new String[tagList.size()];
        int index = 0;
        for(Tag t : tagList){
            names[index] = t.getName();
            index++;
        }
        return names;
    }
    // get Statistic Data (last index is totalNumber)
    public int[] getTagValues()
    {
        int[] values = new int[tagList.size() + 1];
        int totalNumber = 0;
        int index = 0;
        for(Tag t : tagList){
            int[] valuesArray = getNumberOfRelationAnnotationsByTag(t.getName());
            values[index] = valuesArray[0];
            totalNumber = valuesArray[1];
            index++;
        }
        // Last Value is totalNumber
        values[index] = totalNumber;
        return values;
    }
    // Filter
    public static void setFilter(Tag aTag){
        filterLabel = aTag;
    }
    public static Tag getFilter(){
        return filterLabel;
    }

    public void getSentences()
    {
        sentences = new ArrayList<>();
        //LOG.info("SENTENCES:");
        // Get all Sentences and store them
        if(preAnnotated){
            // Get Sentences / Spans from pre-annotated Source File
            for (AnnotationFS sentence : select(cas, getType(cas, SENTENCE_LAYER_NAME))) {
                //LOG.info(sentence.getCoveredText());
                sentences.add(sentence);
            }
            if(sentences.size() < 1){
                // No Sentences found -> Try to use normal Modus / get Sentences
                preAnnotated = false;
            }
        }
        if(!preAnnotated){
            // Get Sentences
            for (AnnotationFS sentence : select(cas, getType(cas, Sentence.class))) {
                //LOG.info(sentence.getCoveredText());
                sentences.add(sentence);
            }
        }

    }
    // get the Annotation of Sentence.class - location somewhere betwen start & end
    // NOT USED
    public AnnotationFS getSentenceAnnotation(int location)
    {
        AnnotationFS result = null;
        for (AnnotationFS sentence : select(cas, getType(cas, Sentence.class))) {
            if(sentence.getBegin() <= location && sentence.getEnd() >= location){
                result = sentence;
            }
        }
        return result;
    }
    public AnnotationFS getSentenceLayerAnnotationFromSentence(AnnotationFS sentence)
    {
        AnnotationFS result = null;
        List selectedAnno = cas.select(getType(cas, SENTENCE_LAYER_NAME)).coveredBy(sentence).asList();
        if(selectedAnno.size() > 0){
            result = (AnnotationFS) selectedAnno.get(0);
        }
        return result;
    }
    // Check if the Doc is a pre-annotated XMI file
    public void getDocAttributes()
    {
        AnnotatorState state = getModelObject();
        if(state.getDocument().getFormat().contains("xmi")){
            // Pre-Annotated File
            preAnnotated = true;
        }
    }
    public void getLayersAndTags()
    {
        //VDocument vdoc = new VDocument();

        //List<Annotation> annotations = new ArrayList<>();

        AnnotatorState state = getModelObject();
        //LOG.info("Annotation Layers");
        //LOG.info(state.getFeatureStates().toString());
        //LOG.info(state.getRememberedArcFeatures().toString());
        //LOG.info(state.getRememberedSpanFeatures().toString());
        // Render visible (custom) layers
        //Map<String[], Queue<String>> colorQueues = new HashMap<>();
        for (AnnotationLayer layer : state.getAnnotationLayers()) {
            //ColoringStrategy coloringStrategy = coloringService.getStrategy(layer,
            //    state.getPreferences(), colorQueues);
            if(layer.getName().equals(SENTENCE_LAYER_NAME)){
                // Sentence Layer
                sentenceLayer = layer;
            }
            if(layer.getName().equals(RELATION_LAYER_NAME)){
                // Relation Layer
                relationLayer = layer;
                // Get Tagset
                for (AnnotationFeature feat : annotationService.listSupportedFeatures(layer)) {
                    // Get Feature(s) -> should be one (with tagset)
                    //LOG.info("Feature: " + feat.getName());
                    if(feat.getTagset() != null){
                        tagSetObject = feat.getTagset();
                        tagList = annotationService.listTags(tagSetObject);
                        feature = feat;
                        //LOG.info("TagList: " + tagSetObject.getName());
                        for (Tag tag : tagList) {
                            //LOG.info(tag.getName());
                        }

                    }
                }
            }
            //LOG.info("Annotation Layer: " + layer.getName());
            //LOG.info(layer.getId().toString());
            //LOG.info(state.getPreferences().getColorPerLayer().toString());
            //if(layer.getAttachFeature() != null ){
            //    LOG.info(layer.getAttachFeature().getName());
            //}
            //if(layer.getAttachFeature() != null && layer.getAttachFeature().getTagset() != null){
            //    LOG.info(layer.getAttachFeature().getTagset().getName());
            //}


            // If the layer is not included in the rendering, then we skip here - but only after
            // we have obtained a coloring strategy for this layer and thus secured the layer
            // color. This ensures that the layer colors do not change depending on the number
            // of visible layers.
            //if (!vdoc.getAnnotationLayers().contains(layer)) {
            //    continue;
            //}
        }

    }

    @Override
    protected void render(AjaxRequestTarget aTarget)
    {
        // Mika: Wann wird die aufgerufen?

        // REC: I didn't find a good way of clearing the annotations, so we do it the hard way:
        // - rerender the entire document
        // - re-add all the annotations
        //aTarget.add(vis);
        //aTarget.appendJavaScript(initAnnotatorJs(vis, storeAdapter));


//        aTarget.appendJavaScript(WicketUtil.wrapInTryCatch(String.join("\n",
//            "$('#" + vis.getMarkupId() + "').data('annotator').plugins.Store._getAnnotations();",
//            "$('#" + vis.getMarkupId() + "').data('annotator').plugins.Store._getAnnotations();"
//        )));
    }

    private void handleError(String aMessage, Throwable aCause)
    {
        LOG.error(aMessage, aCause);
        error(aMessage + ExceptionUtils.getRootCauseMessage(aCause));
        return;
    }

    private void saveCas()
    {
        try{
            AnnotationPageBase annotationPage = findParent(AnnotationPageBase.class);
            annotationPage.writeEditorCas(cas);
        }catch (IOException | AnnotationException e){
            handleError("Unable to save Annotation", e);
        }
    }

    private void annotateSentence(AnnotationFS aSentence){
        try {
            int begin = aSentence.getBegin();
            int end = aSentence.getEnd();

            // Check if Sentence has already been annotated with SentenceLayer
            if(!isTextAnnotated(begin, end, sentenceLayer)){
                // No Features - Empty Supplier
                Supplier supplier = new Supplier() {
                    @Override
                    public Object get() {
                        return null;
                    }
                };
                SpanAdapter adapter = (SpanAdapter) layerSupportRegistry.getLayerSupport(sentenceLayer).createAdapter(sentenceLayer, supplier);
                //AnnotationFS annotation = cas.createAnnotation(getType(cas, SENTENCE_LAYER_NAME), begin, end);
                AnnotatorState state = getModelObject();
                SourceDocument doc = state.getDocument();
                String username = state.getUser().getUsername();
                adapter.add(doc, username, cas, begin, end);
                //cas.addFsToIndexes(annotation);
                //adapter.setFeatureValue(doc, username, cas, WebAnnoCasUtil.getAddr(annotation), feature, value);
            }
        }
        catch (Exception e) {
            handleError("Unable to create span annotation", e);
        }
    }
    // Checks if a part of a text as an Annotation of type aAnnotationLayer
    private boolean isTextAnnotated(int begin, int end, AnnotationLayer aAnnotationLayer){
        boolean alreadyAnnotated = false;
        try{
            List selectedAnnos = cas.select(getType(cas,aAnnotationLayer.getName())).coveredBy(begin, end).asList();
            if(selectedAnnos.size() > 0){
                alreadyAnnotated = true;
                //LOG.info("AlreadyAnnotated");
                //LOG.info(selectedAnnos.toString());
            }
        }catch (Exception e){
            handleError("Unable to check Annotated Text", e);
            //LOG.info("AlreadyAnnotated");
            //LOG.info("false");
            return false;
        }
        //LOG.info("AlreadyAnnotated");
        //LOG.info(alreadyAnnotated ? "true" : "false");
        return alreadyAnnotated;
    }
    // Checks if Relation already exists and updates View
    private void updateSentenceRelation(){
        AnnotationFS leftSentenceAnnoFS = sentences.get(leftSentenceIndex);
        AnnotationFS rightSentenceAnnoFS = sentences.get(rightSentenceIndex);
        List<Annotation> selectedAnnoList_Left = cas.<Annotation>select(getType(cas,RELATION_LAYER_NAME))
            .coveredBy(leftSentenceAnnoFS).asList();
        List<Annotation> selectedAnnoList_Right = cas.<Annotation>select(getType(cas,RELATION_LAYER_NAME))
            .coveredBy(rightSentenceAnnoFS).asList();
        //("Detected annos");
        Tag[] result1 = getRelationTags(selectedAnnoList_Left, leftSentenceAnnoFS, rightSentenceAnnoFS);
        Tag[] result2 = getRelationTags(selectedAnnoList_Right, leftSentenceAnnoFS, rightSentenceAnnoFS);
        // Update Relation
        relation.setSentences(sentence1.getObject(), sentence2.getObject());
        Tag leftRelation = (
            result1[0] != null ? result1[0] :
                result2[0] != null ? result2[0] : null
        );
        Tag rightRelation = (
            result1[1] != null ? result1[1] :
                result2[1] != null ? result2[1] : null
        );
        relation.setRelationLeft(leftRelation);
        relation.setRelationRight(rightRelation);

    }
    // Compares the Annotations from annotationList with leftSentence & rightSentence and update View
    private Tag[] getRelationTags(
        List<Annotation> annotationList, AnnotationFS leftSentence, AnnotationFS rightSentence
    ){
        Tag result[] = new Tag[2];
        result[0] = null;
        result[1] = null;
        for (Annotation potentialRelation : annotationList) {
            // Dependent
            Feature dependentFeat = potentialRelation.getType().getFeatureByBaseName("Dependent");
            AnnotationFS dependentFS = (AnnotationFS) potentialRelation.getFeatureValue(dependentFeat);
            // Governor
            Feature governorFeat = potentialRelation.getType().getFeatureByBaseName("Governor");
            AnnotationFS governorFS = (AnnotationFS) potentialRelation.getFeatureValue(governorFeat);
            // Label
            Feature label = potentialRelation.getType().getFeatureByBaseName("label");
            String labelString = potentialRelation.getFeatureValueAsString(label);
            if(
                isSameSentence(leftSentence, dependentFS)
                    && isSameSentence(rightSentence, governorFS)
            ){
                // Detected Relation from right to left
                for(Tag t : tagList){
                    if(t.getName().equals(labelString)){
                        result[0] = t;
                    }
                }
            }
            if(
                isSameSentence(rightSentence, dependentFS)
                    && isSameSentence(leftSentence, governorFS)
            ){
                // Detected Relation from left to right
                for(Tag t : tagList){
                    if(t.getName().equals(labelString)){
                        result[1] = t;
                    }
                }
            }
        }
        return result;
    }
    // get Relation Annotation if Exists
    private Annotation getRelationAnnotation(
        AnnotationFS originFS, AnnotationFS targetFS
    ){
        Annotation result = null;
        List<Annotation> selectedAnnoList = cas.<Annotation>select(getType(cas,RELATION_LAYER_NAME))
            .coveredBy(targetFS).asList();
        for (Annotation potentialRelation : selectedAnnoList) {
            // Dependent
            Feature dependentFeat = potentialRelation.getType().getFeatureByBaseName("Dependent");
            AnnotationFS dependentFS = (AnnotationFS) potentialRelation.getFeatureValue(dependentFeat);
            // Governor
            Feature governorFeat = potentialRelation.getType().getFeatureByBaseName("Governor");
            AnnotationFS governorFS = (AnnotationFS) potentialRelation.getFeatureValue(governorFeat);
            if(
                isSameSentence(targetFS, dependentFS)
                    && isSameSentence(originFS, governorFS)
            ){
                result = potentialRelation;
            }
        }

        return result;
    }

    private void createSentenceAnnotation()
    {

        //long start = System.nanoTime();
        //LOG.info("createAnno 1:" + (System.nanoTime() - start));
        //start = System.nanoTime();
        // Annotation first Sentence
        annotateSentence(sentences.get(leftSentenceIndex));
        //LOG.info("createAnno 2:" + (System.nanoTime() - start));
        //start = System.nanoTime();
        // Annotation second Sentence
        annotateSentence(sentences.get(rightSentenceIndex));
        //LOG.info("createAnno 3:" + (System.nanoTime() - start));
        //start = System.nanoTime();


        // Annotate sentence 1
        //if (begin_sentence1 > -1 && end_sentence1 > -1) {
        //    if (state.isSlotArmed()) {
        // When filling a slot, the current selection is *NOT* changed. The
        // Span annotation which owns the slot that is being filled remains
        // selected!
        //        getActionHandler().actionFillSlot(aTarget, cas, begin_sentence1, end_sentence1, NONE_ID);
        //    }
        //    else {
        // DOES NOT WORK SELECTING
        //        state.setSelectedAnnotationLayer(sentenceLayer);
        //        state.getSelection().selectSpan(cas, begin_sentence1, end_sentence1);
        //        getActionHandler().actionCreateOrUpdate(aTarget, cas);
        //    }
        //}
        //else {
        //    handleError("Unable to create span annotation: No match was found", aTarget);
        //}
        // Annotate sentence 2
        //if (begin_sentence2 > -1 && end_sentence2 > -1) {
        //    if (state.isSlotArmed()) {
        // When filling a slot, the current selection is *NOT* changed. The
        // Span annotation which owns the slot that is being filled remains
        // selected!
        //        getActionHandler().actionFillSlot(aTarget, cas, begin_sentence2, end_sentence2, NONE_ID);
        //    }
        //    else {
        //        state.setSelectedAnnotationLayer(sentenceLayer);
        //        state.getSelection().selectSpan(cas, begin_sentence2, end_sentence2);
        //        getActionHandler().actionCreateOrUpdate(aTarget, cas);
        //    }
        //}
        //else {
        //    handleError("Unable to create span annotation: No match was found", aTarget);
        //}
    }

    // Creates a Relation Annotation from aSentenceIndex to bSentenceIndex with tag
    private void createRelationAnnotation(Tag aTag, int originIndex, int targetIndex){
        try {
            AnnotatorState state = getModelObject();
            SourceDocument doc = state.getDocument();
            String username = state.getUser().getUsername();
            // Get Sentences
            AnnotationFS sentence1 = sentences.get(originIndex);
            AnnotationFS sentence2 = sentences.get(targetIndex);
            //List selectedAnno = cas.select(getType(cas, SENTENCE_LAYER_NAME)).coveredBy(sentence1).asList();
            AnnotationFS originFS = sentence1;
            AnnotationFS targetFS = sentence2;
            if(!preAnnotated){
                originFS = getSentenceLayerAnnotationFromSentence(sentence1);
                targetFS = getSentenceLayerAnnotationFromSentence(sentence2);
            }

            // Gets the Origin & Target of Type SENTENCE_LAYER_NAME
            //AnnotationFS originFs = cas.createAnnotation(getType(cas, SENTENCE_LAYER_NAME), sentence1.getBegin(), sentence1.getEnd());
            //AnnotationFS targetFs = cas.createAnnotation(getType(cas, SENTENCE_LAYER_NAME), sentence2.getBegin(), sentence2.getEnd());
            // Get state
            //AnnotatorState state = getModelObject();
            // Select
            //Selection selection = state.getSelection();
            //selection.selectArc(VID.NONE_ID, originFs, targetFs);
            // Create
            //getActionHandler().actionCreateOrUpdate(aTarget, cas);

            // Second try
            Supplier supplier = new RelationFeatureSupplier(relationLayer);
            RelationAdapter adapter = (RelationAdapter) layerSupportRegistry.getLayerSupport(relationLayer)
                .createAdapter(relationLayer, supplier);
            // Delete if prev Relation is detected
            // TODO: REMAIN META DATA!
            Annotation prevAnno = getRelationAnnotation(originFS, targetFS);
            if(prevAnno != null){
                VID vid = new VID(WebAnnoCasUtil.getAddr(prevAnno));
                adapter.delete(doc, username, cas, vid);
            }
            // Create new Relation
            AnnotationFS annotation = adapter.add(doc, username, originFS, targetFS, cas);
            adapter.setFeatureValue(doc, username, cas, WebAnnoCasUtil.getAddr(annotation), feature, aTag.getName());
            //LOG.info("ANNOTAA: " + aTag.getName());
            if(aTag.getName().equals(EMPTY_FEATURE)){
                if(alreadyTaggedPairs.contains(pairIndex)){
                    alreadyTaggedPairs.remove(pairIndex);
                    LOG.info("REMOVED ANNO PROGRESS");
                }
            }else{
                //LOG.info(alreadyTaggedPairs.toString());
                //LOG.info(" " + pairIndex);
                if(!alreadyTaggedPairs.contains(pairIndex)){
                    // Check if other direction is already annotated and flag already tagged if so
                    Annotation inverseAnno = getRelationAnnotation(targetFS, originFS);
                    //LOG.info("INVERSE ANNO");
                    //LOG.info(inverseAnno.toString());
                    if(inverseAnno != null){
                        Feature label = inverseAnno.getType().getFeatureByBaseName("label");
                        String label_string = inverseAnno.getFeatureValueAsString(label);
                        //LOG.info("PREV FEAT " + label_string);
                        if(!label_string.equals("") && !label_string.equals(EMPTY_FEATURE)){
                            alreadyTaggedPairs.add(pairIndex);
                            //LOG.info("ADDED ANNO PROGRESS");
                        }
                    }

                }
            }
            progressPanel.setTaggedPairs(alreadyTaggedPairs);
        }
        catch (CASRuntimeException | AnnotationException e)
        {
            handleError("Unable to create relation annotation", e);
        }
    }

    // OLD UNUSED


    private String toJson(Object result)
    {
        String json = "[]";
        try {
            json = JSONUtil.toInterpretableJsonString(result);
        }
        catch (IOException e) {
            error("Unable to produce JSON response " + ":" + ExceptionUtils.getRootCauseMessage(e));
        }
        return json;
    }

    
    private void handleError(String aMessage, Throwable aCause, AjaxRequestTarget aTarget)
    {
        LOG.error(aMessage, aCause);
        handleError(aMessage + ": " + ExceptionUtils.getRootCauseMessage(aCause), aTarget);
    }

    private void handleError(String aMessage, AjaxRequestTarget aTarget)
    {
        error(aMessage);
        aTarget.addChildren(getPage(), IFeedback.class);
    }


    private static class Node
    {
        int position;
        String type;
    }
}
