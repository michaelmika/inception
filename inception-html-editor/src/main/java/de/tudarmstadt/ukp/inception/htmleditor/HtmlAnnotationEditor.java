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

import static de.tudarmstadt.ukp.clarin.webanno.api.WebAnnoConst.CHAIN_TYPE;
import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.VID.NONE_ID;
import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.TypeUtil.getUiLabelText;
import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil.getAddr;
import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil.selectByAddr;
import static javax.xml.transform.OutputKeys.INDENT;
import static javax.xml.transform.OutputKeys.METHOD;
import static javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION;
import static org.apache.uima.fit.util.CasUtil.getType;
import static org.apache.uima.fit.util.CasUtil.select;
import static org.apache.uima.fit.util.CasUtil.selectCovered;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import de.tudarmstadt.ukp.clarin.webanno.api.WebAnnoConst;
import org.apache.uima.cas.Feature;
import org.apache.uima.fit.util.CasUtil;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.tcas.Annotation;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.adapter.SpanAdapter;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.layer.LayerSupportRegistry;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.Selection;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil;
import de.tudarmstadt.ukp.clarin.webanno.model.*;
import de.tudarmstadt.ukp.inception.htmleditor.textRelationsAnnotator.TextRelationsCssResourceReference;
import de.tudarmstadt.ukp.inception.htmleditor.textRelationsAnnotator.TextRelationsJavascriptResourceReference;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.page.AnnotationPageBase;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.adapter.RelationAdapter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.CASRuntimeException;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.jcas.cas.TOP;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AbstractDefaultAjaxBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.feedback.IFeedback;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.head.OnDomReadyHeaderItem;
import org.apache.wicket.markup.html.WebComponent;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;

import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.FormComponentUpdatingBehavior;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.request.IRequestParameters;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.apache.wicket.util.string.Strings;
import org.checkerframework.checker.units.qual.A;
import org.dkpro.core.api.metadata.Tagset;
import org.dkpro.core.api.xml.Cas2SaxEvents;
import org.dkpro.core.api.xml.type.XmlDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import de.tudarmstadt.ukp.clarin.webanno.api.AnnotationSchemaService;
import de.tudarmstadt.ukp.clarin.webanno.api.CasProvider;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.AnnotationEditorBase;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.AnnotationEditorExtensionRegistry;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.action.AnnotationActionHandler;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.adapter.TypeAdapter;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.coloring.ColoringRules;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.coloring.ColoringRulesTrait;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.coloring.ColoringService;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.coloring.ColoringStrategy;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.exception.AnnotationException;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.AnnotatorState;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.VID;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.PreRenderer;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.model.VDocument;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.model.VRange;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.model.VSpan;
import de.tudarmstadt.ukp.clarin.webanno.support.JSONUtil;
import de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaModel;
import de.tudarmstadt.ukp.clarin.webanno.support.wicket.WicketUtil;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Div;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Heading;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Paragraph;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.inception.htmleditor.annotatorjs.model.Range;
import de.tudarmstadt.ukp.inception.htmleditor.annotatorjs.resources.AnnotatorJsCssResourceReference;
import de.tudarmstadt.ukp.inception.htmleditor.annotatorjs.resources.AnnotatorJsJavascriptResourceReference;

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

    private static final String SENTENCE_LAYER_NAME = "webanno.custom.Sentence";
    private static final String RELATION_LAYER_NAME = "webanno.custom.SentenceRelation";
    private AnnotationLayer sentenceLayer, relationLayer;
    private TagSet tagSetObject;
    private List<Tag> tagList;
    private AnnotationFeature feature;
    private TextRelation relation;

    private boolean preAnnotated = false;

    // get new Sentence (after navigation) logic
    // PREANNOTATED!
    public int getNewSentenceIndex(int index1, int index2, String method){
        int tmp = 0;
        if(sentences.size() < 2){
            return tmp;
        }
        switch (method){
            case "next":
                LOG.info("Method next");
                if (index1 + 1 > sentences.size() - 1) {
                    tmp = 0;
                }else{
                    tmp = index1 + 1;
                }
                if (tmp == index2) {
                    tmp = getNewSentenceIndex(tmp, index2, method);
                }
                break;
            case "previous":
                LOG.info("Method previous");
                if (index1 - 1 < 0) {
                    tmp = sentences.size() - 1;
                }else{
                    tmp = index1 - 1;
                }
                if (tmp == index2) {
                    tmp = getNewSentenceIndex(tmp, index2, method);
                }
                break;
            default:
                break;
        }
        return tmp;
    }

    public boolean isSameSentence(AnnotationFS anno1, AnnotationFS anno2){
        return anno1.getBegin() == anno2.getBegin() && anno1.getEnd() == anno2.getEnd();
    }

    public HtmlAnnotationEditor(String aId, IModel<AnnotatorState> aModel,
            AnnotationActionHandler aActionHandler, CasProvider aCasProvider)
    {
        super(aId, aModel, aActionHandler, aCasProvider);
        getDocAttributes();
        getSentences();
        getLayersAndTags();
        renderTextRelations();
    }
    public String getSegmentPositionString(int index)
    {
        String result = "";
        if(preAnnotated){

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
        }
        // Navigation Buttons
        form.add(new AjaxButton("textLeftPrevious"){
            @Override
            protected void onSubmit(AjaxRequestTarget target){
                super.onSubmit(target);
                leftSentenceIndex = getNewSentenceIndex(leftSentenceIndex, rightSentenceIndex, "previous");
                leftIndexModel.setObject(leftSentenceIndex);
                sentence1.setObject(sentences.get(leftSentenceIndex).getCoveredText());
                positionString1.setObject("(" + (leftSentenceIndex + 1) + "/" + sentences.size() + ")");
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
                positionString1.setObject("(" + (leftSentenceIndex + 1) + "/" + sentences.size() + ")");
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
                positionString2.setObject("(" + (rightSentenceIndex + 1) + "/" + sentences.size() + ")");
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
                positionString2.setObject("(" + (rightSentenceIndex + 1) + "/" + sentences.size() + ")");
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
            positionString1 = Model.of("(" + (leftSentenceIndex + 1) + "/" + sentences.size() + ")");
            textLeft = new Label("textLeft", sentence1);
            positionLabel1 = new Label("textLeftPosition", positionString1);
            positionLabel1.setOutputMarkupId(true);
            textLeft.setOutputMarkupId(true);
            // Sentence Right
            sentence2 = Model.of(sentences.get(rightSentenceIndex).getCoveredText());
            positionString2 = Model.of("(" + (rightSentenceIndex + 1) + "/" + sentences.size() + ")");
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
                    Tag tag = (Tag) getFormComponent().getModelObject();
                    relation.setRelationLeft(tag);
                    LOG.info("Relation Left Choice: " + tag.getName());
                    // Annotate both active Sentences
                    createSentenceAnnotation();
                    // Annotate relation
                    createRelationAnnotation(tag, rightSentenceIndex, leftSentenceIndex);
                    renderTextRelations(aTarget);
                }
            }));

        }
        form.add(positionLabel1);
        form.add(positionLabel2);
        form.add(textLeft);
        form.add(textRight);



        return form;
    }
    public void renderTextRelations(){
        form = createForm();
        updateSentenceRelation();
        this.add(form);
    }
    public void renderTextRelations(AjaxRequestTarget aTarget){
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
    public void getSentences()
    {
        CAS cas;
        try {
            cas = getCasProvider().get();
        }
        catch (IOException e) {
            handleError("Unable to load data", e);
            return;
        }
        sentences = new ArrayList<>();
        LOG.info("SENTENCES:");
        // Get all Sentences and store them
        if(preAnnotated){
            // Get Sentences / Spans from pre-annotated Source File
            for (AnnotationFS sentence : select(cas, getType(cas, SENTENCE_LAYER_NAME))) {
                LOG.info(sentence.getCoveredText());
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
                LOG.info(sentence.getCoveredText());
                sentences.add(sentence);
            }
        }

    }
    // get the Annotation of Sentence.class - location somewhere betwen start & end
    // NOT USED
    public AnnotationFS getSentenceAnnotation(int location){
        CAS cas;
        AnnotationFS result = null;
        try {
            cas = getCasProvider().get();
            for (AnnotationFS sentence : select(cas, getType(cas, Sentence.class))) {
                if(sentence.getBegin() <= location && sentence.getEnd() >= location){
                    result = sentence;
                }
            }
        }
        catch (IOException e) {
            handleError("Unable to load data", e);
            return null;
        }
        return result;
    }
    public AnnotationFS getSentenceLayerAnnotation(AnnotationFS sentence){
        CAS cas;
        AnnotationFS result = null;
        try {
            cas = getCasProvider().get();
            List selectedAnno = cas.select(getType(cas, SENTENCE_LAYER_NAME)).coveredBy(sentence).asList();
            if(selectedAnno.size() > 0){
                result = (AnnotationFS) selectedAnno.get(0);
            }

        }
        catch (IOException e) {
            handleError("Unable to load data", e);
            return null;
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
        LOG.info("Annotation Layers");
        LOG.info(state.getFeatureStates().toString());
        LOG.info(state.getRememberedArcFeatures().toString());
        LOG.info(state.getRememberedSpanFeatures().toString());
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
                    LOG.info("Feature: " + feat.getName());
                    if(feat.getTagset() != null){
                        tagSetObject = feat.getTagset();
                        tagList = annotationService.listTags(tagSetObject);
                        feature = feat;
                        LOG.info("TagList: " + tagSetObject.getName());
                        for (Tag tag : tagList) {
                            LOG.info(tag.getName());
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

    private void annotateSentence(CAS aCas, AnnotationFS aSentence){
        try {
            int begin = aSentence.getBegin();
            int end = aSentence.getEnd();

            // Check if Sentence has already been annotated with SentenceLayer
            if(!isTextAnnotated(aCas, begin, end, sentenceLayer)){
                // No Features - Empty Supplier
                Supplier supplier = new Supplier() {
                    @Override
                    public Object get() {
                        return null;
                    }
                };
                SpanAdapter adapter = (SpanAdapter) layerSupportRegistry.getLayerSupport(sentenceLayer).createAdapter(sentenceLayer, supplier);
                //AnnotationFS annotation = aCas.createAnnotation(getType(aCas, SENTENCE_LAYER_NAME), begin, end);
                AnnotatorState state = getModelObject();
                SourceDocument doc = state.getDocument();
                String username = state.getUser().getUsername();
                adapter.add(doc, username, aCas, begin, end);
                //aCas.addFsToIndexes(annotation);
                //adapter.setFeatureValue(doc, username, aCas, WebAnnoCasUtil.getAddr(annotation), feature, value);
            }
        }
        catch (Exception e) {
            handleError("Unable to create span annotation", e);
        }
    }
    // Checks if a part of a text as an Annotation of type aAnnotationLayer
    private boolean isTextAnnotated(CAS aCas, int begin, int end, AnnotationLayer aAnnotationLayer){
        boolean alreadyAnnotated = false;
        try{
            List selectedAnnos = aCas.select(getType(aCas,aAnnotationLayer.getName())).coveredBy(begin, end).asList();
            if(selectedAnnos.size() > 0){
                alreadyAnnotated = true;
                LOG.info("AlreadyAnnotated");
                LOG.info(selectedAnnos.toString());
            }
        }catch (Exception e){
            handleError("Unable to check Annotated Text", e);
            LOG.info("AlreadyAnnotated");
            LOG.info("false");
            return false;
        }
        LOG.info("AlreadyAnnotated");
        LOG.info(alreadyAnnotated ? "true" : "false");
        return alreadyAnnotated;
    }
    // Checks if Relation already exists and updates View
    private void updateSentenceRelation(){
        try {
            CAS cas = getCasProvider().get();
            AnnotationFS leftSentenceAnnoFS = sentences.get(leftSentenceIndex);
            AnnotationFS rightSentenceAnnoFS = sentences.get(rightSentenceIndex);
            List<Annotation> selectedAnnoList_Left = cas.<Annotation>select(getType(cas,RELATION_LAYER_NAME))
                .coveredBy(leftSentenceAnnoFS).asList();
            List<Annotation> selectedAnnoList_Right = cas.<Annotation>select(getType(cas,RELATION_LAYER_NAME))
                .coveredBy(rightSentenceAnnoFS).asList();
            LOG.info("Detected annos");
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

        } catch (IOException e) {
            handleError("Unable to create span annotation", e);
        }
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
        try{
            CAS cas = getCasProvider().get();
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

        }catch (IOException e){
            handleError("Unable to create span annotation", e);
        }
        return result;
    }

    private void createSentenceAnnotation()
    {

        try {
            CAS cas = getCasProvider().get();
            // Annotation first Sentence
            annotateSentence(cas, sentences.get(leftSentenceIndex));
            // Annotation second Sentence
            annotateSentence(cas, sentences.get(rightSentenceIndex));
            AnnotationPageBase annotationPage = findParent(AnnotationPageBase.class);
            annotationPage.writeEditorCas(cas);

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
        catch (IOException | AnnotationException e) {
            handleError("Unable to create span annotation", e);
        }
    }

    // Creates a Relation Annotation from aSentenceIndex to bSentenceIndex with tag
    private void createRelationAnnotation(Tag aTag, int originIndex, int targetIndex){
        try {
            CAS aCas = getCasProvider().get();
            AnnotatorState state = getModelObject();
            SourceDocument doc = state.getDocument();
            String username = state.getUser().getUsername();
            // Get Sentences
            AnnotationFS sentence1 = sentences.get(originIndex);
            AnnotationFS sentence2 = sentences.get(targetIndex);
            //List selectedAnno = aCas.select(getType(aCas, SENTENCE_LAYER_NAME)).coveredBy(sentence1).asList();
            AnnotationFS originFS = sentence1;
            AnnotationFS targetFS = sentence2;
            if(!preAnnotated){
                originFS = getSentenceLayerAnnotation(sentence1);
                targetFS = getSentenceLayerAnnotation(sentence2);
            }

            // Gets the Origin & Target of Type SENTENCE_LAYER_NAME
            //AnnotationFS originFs = aCas.createAnnotation(getType(aCas, SENTENCE_LAYER_NAME), sentence1.getBegin(), sentence1.getEnd());
            //AnnotationFS targetFs = aCas.createAnnotation(getType(aCas, SENTENCE_LAYER_NAME), sentence2.getBegin(), sentence2.getEnd());
            // Get state
            //AnnotatorState state = getModelObject();
            // Select
            //Selection selection = state.getSelection();
            //selection.selectArc(VID.NONE_ID, originFs, targetFs);
            // Create
            //getActionHandler().actionCreateOrUpdate(aTarget, aCas);

            // Second try
            Supplier supplier = new RelationFeatureSupplier(relationLayer);
            RelationAdapter adapter = (RelationAdapter) layerSupportRegistry.getLayerSupport(relationLayer)
                .createAdapter(relationLayer, supplier);
            // Delete if prev Relation is detected
            Annotation prevAnno = getRelationAnnotation(originFS, targetFS);
            if(prevAnno != null){
                VID vid = new VID(WebAnnoCasUtil.getAddr(prevAnno));
                adapter.delete(doc, username, aCas, vid);
            }
            // Create new Relation
            AnnotationFS annotation = adapter.add(doc, username, originFS, targetFS, aCas);
            adapter.setFeatureValue(doc, username, aCas, WebAnnoCasUtil.getAddr(annotation), feature, aTag.getName());
            AnnotationPageBase annotationPage = findParent(AnnotationPageBase.class);
            annotationPage.writeEditorCas(aCas);
        }
        catch (IOException | CASRuntimeException | AnnotationException e)
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
