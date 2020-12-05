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
import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil.selectByAddr;
import static javax.xml.transform.OutputKeys.INDENT;
import static javax.xml.transform.OutputKeys.METHOD;
import static javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION;
import static org.apache.uima.fit.util.CasUtil.getType;
import static org.apache.uima.fit.util.CasUtil.select;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.stream.Collectors;

import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import de.tudarmstadt.ukp.clarin.webanno.model.*;
import de.tudarmstadt.ukp.inception.htmleditor.textRelationsAnnotator.TextRelationsCssResourceReference;
import de.tudarmstadt.ukp.inception.htmleditor.textRelationsAnnotator.TextRelationsJavascriptResourceReference;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AbstractDefaultAjaxBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.feedback.IFeedback;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.head.OnDomReadyHeaderItem;
import org.apache.wicket.markup.html.WebComponent;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;

import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.FormComponentUpdatingBehavior;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.request.IRequestParameters;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.apache.wicket.util.string.Strings;
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
import de.tudarmstadt.ukp.inception.htmleditor.annotatorjs.model.Annotation;
import de.tudarmstadt.ukp.inception.htmleditor.annotatorjs.model.Range;
import de.tudarmstadt.ukp.inception.htmleditor.annotatorjs.resources.AnnotatorJsCssResourceReference;
import de.tudarmstadt.ukp.inception.htmleditor.annotatorjs.resources.AnnotatorJsJavascriptResourceReference;

public class HtmlAnnotationEditor
    extends AnnotationEditorBase
{
    private static final long serialVersionUID = -3358207848681467993L;
    private static final Logger LOG = LoggerFactory.getLogger(HtmlAnnotationEditor.class);

    private StoreAdapter storeAdapter;

    private @SpringBean PreRenderer preRenderer;
    private @SpringBean AnnotationSchemaService annotationService;
    private @SpringBean AnnotationEditorExtensionRegistry extensionRegistry;
    private @SpringBean ColoringService coloringService;

    // eigene Variablen
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
    private TextRelation relation;

    // get new Sentence (after navigation) logic
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

    public HtmlAnnotationEditor(String aId, IModel<AnnotatorState> aModel,
            AnnotationActionHandler aActionHandler, CasProvider aCasProvider)
    {
        super(aId, aModel, aActionHandler, aCasProvider);
        LOG.info("HTML:");
        LOG.info(this.renderHtml());
        //vis = new Label("vis", LambdaModel.of(this::renderHtml));
        //vis.setOutputMarkupId(true);
        //vis.setEscapeModelStrings(false);
        //add(vis);
        getSentences();
        getLayersAndTags();
        renderTextRelations();

        storeAdapter = new StoreAdapter();
        add(storeAdapter);
    }

    public Form createForm(){
        Form<Void> form = new Form<Void>("textRelationForm");

        // Navigation Buttons
        form.add(new AjaxButton("textLeftPrevious"){
            @Override
            protected void onSubmit(AjaxRequestTarget target){
                super.onSubmit(target);
                leftSentenceIndex = getNewSentenceIndex(leftSentenceIndex, rightSentenceIndex, "previous");
                LOG.info("Button Click");
                LOG.info("" + leftSentenceIndex);
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
                LOG.info("Button Click");
                LOG.info("" + leftSentenceIndex);
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
                LOG.info("Button Click");
                LOG.info("" + rightSentenceIndex);
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
                LOG.info("Button Click");
                LOG.info("" + rightSentenceIndex);
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
            // DropDown for Right Relation
            form.add(new DropDownChoice<Tag>(
                "relationRight",
                new PropertyModel<Tag>(relation, "relationRight"),
                new LoadableDetachableModel<List<Tag>>() {
                    @Override
                    protected List<Tag> load() {
                        return tagList;
                    }
                }
            ).add(new FormComponentUpdatingBehavior() {
                /**
                 * Called when a option is selected of a dropdown list.
                 */
                protected void onUpdate() {
                    Tag tag = (Tag) getFormComponent().getModelObject();
                    relation.setRelationRight(tag);
                    LOG.info("Relation Right Choice: " + tag.getName());
                }
            }));
            // DropDown for Left Relation
            form.add(new DropDownChoice<Tag>(
                "relationLeft",
                new PropertyModel<Tag>(relation, "relationLeft"),
                new LoadableDetachableModel<List<Tag>>() {
                    @Override
                    protected List<Tag> load() {
                        return tagList;
                    }
                }
            ).add(new FormComponentUpdatingBehavior() {
                /**
                 * Called when a option is selected of a dropdown list.
                 */
                protected void onUpdate() {
                    Tag tag = (Tag) getFormComponent().getModelObject();
                    relation.setRelationLeft(tag);
                    LOG.info("Relation Left Choice: " + tag.getName());
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
        this.add(form);
    }
    public void renderTextRelations(AjaxRequestTarget aTarget){
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
        for (AnnotationFS sentence : select(cas, getType(cas, Sentence.class))) {
            LOG.info(sentence.getCoveredText());
            sentences.add(sentence);
        }
    }
    public String renderHtml()
    {
        CAS cas;
        try {
            cas = getCasProvider().get();
        }
        catch (IOException e) {
            handleError("Unable to load data", e);
            return "";
        }
        LOG.info("Annotation");
        LOG.info(cas.getDocumentAnnotation().toString());
        LOG.info("CAS");
        LOG.info(cas.toString());

        try {
            if (cas.select(XmlDocument.class).isEmpty()) {
                return renderLegacyHtml(cas);
            }
            else {
                return renderHtmlDocumentStructure(cas);
            }
        }
        catch (Exception e) {
            handleError("Unable to render data", e);
            return "";
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

    private String renderHtmlDocumentStructure(CAS aCas)
        throws IOException, TransformerConfigurationException, CASException, SAXException
    {
        try (Writer out = new StringWriter()) {
            SAXTransformerFactory tf = (SAXTransformerFactory) TransformerFactory.newInstance();
            tf.setFeature("http://javax.xml.XMLConstants/feature/secure-processing", true);
            TransformerHandler th = tf.newTransformerHandler();
            th.getTransformer().setOutputProperty(OMIT_XML_DECLARATION, "yes");
            th.getTransformer().setOutputProperty(METHOD, "xml");
            th.getTransformer().setOutputProperty(INDENT, "no");
            th.setResult(new StreamResult(out));
            
            Cas2SaxEvents serializer = new Cas2SaxEvents(th);
            serializer.process(aCas.getJCas());
            return out.toString();
        }
    }
    
    private String renderLegacyHtml(CAS aCas)
    {
        StringBuilder buf = new StringBuilder(Strings.escapeMarkup(aCas.getDocumentText()));

        List<Node> nodes = new ArrayList<>();
        for (AnnotationFS div : select(aCas, getType(aCas, Div.class))) {
            if (div.getType().getName().equals(Paragraph.class.getName())) {
                Node startNode = new Node();
                startNode.position = div.getBegin();
                startNode.type = "<p>";
                nodes.add(startNode);

                Node endNode = new Node();
                endNode.position = div.getEnd();
                endNode.type = "</p>";
                nodes.add(endNode);
            }
            if (div.getType().getName().equals(Heading.class.getName())) {
                Node startNode = new Node();
                startNode.position = div.getBegin();
                startNode.type = "<h1>";
                nodes.add(startNode);

                Node endNode = new Node();
                endNode.position = div.getEnd();
                endNode.type = "</h1>";
                nodes.add(endNode);
            }
        }

        // Sort backwards
        nodes.sort((a, b) -> {
            return b.position - a.position;
        });

        for (Node n : nodes) {
            buf.insert(n.position, n.type);
        }

        return buf.toString();
    }

    private void handleError(String aMessage, Throwable aCause)
    {
        LOG.error(aMessage, aCause);
        error(aMessage + ExceptionUtils.getRootCauseMessage(aCause));
        return;
    }

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

    private String initAnnotatorJs(WebComponent aContainer, StoreAdapter aAdapter)
    {
        String callbackUrl = aAdapter.getCallbackUrl().toString();
        LOG.info(callbackUrl);
        StringBuilder script = new StringBuilder();
        script.append(
                "var ann = $('#" + aContainer.getMarkupId() + "').annotator({readOnly: false});");
        script.append("ann.annotator('addPlugin', 'Store', {");
        script.append("    prefix: null,");
        script.append("    emulateJSON: true,");
        script.append("    emulateHTTP: true,");
        script.append("    urls: {");
        script.append("        read:    '" + callbackUrl + "',");
        script.append("        create:  '" + callbackUrl + "',");
        script.append("        update:  '" + callbackUrl + "',");
        script.append("        destroy: '" + callbackUrl + "',");
        script.append("        search:  '" + callbackUrl + "',");
        script.append("        select:  '" + callbackUrl + "'");
        script.append("    }");
        script.append("});");
        //script.append("Wicket.$('" + vis.getMarkupId() + "').annotator = ann;");
        return WicketUtil.wrapInTryCatch(script.toString());
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
    
    private class StoreAdapter
        extends AbstractDefaultAjaxBehavior
    {
        private static final long serialVersionUID = -7919362960963563800L;

        @Override
        protected void respond(AjaxRequestTarget aTarget)
        {
            // We always refresh the feedback panel - only doing this in the case were actually
            // something worth reporting occurs is too much of a hassel...
            aTarget.addChildren(getPage(), IFeedback.class);

            final IRequestParameters reqParams = getRequest().getRequestParameters();

            // We use "emulateHTTP" to get the method as a parameter - this makes it easier to
            // access the method without having to go to the native container request.
            String method = reqParams.getParameterValue("_method").toString();

            // We use "emulateJSON" to get the JSON payload as a parameter - again makes it
            // easier to access the payload without having to go to the native container request.
            String payload = reqParams.getParameterValue("json").toString();

            LOG.debug("[" + method + "]: " + payload);
            try {
                // Loading existing annotations
                if ("GET".equals(method)) {
                    read(aTarget);
                }

                // Update existing annotation
                if ("PUT".equals(method) && StringUtils.isNotEmpty(payload)) {
                    update(aTarget, payload);
                }

                // New annotation created
                if ("POST".equals(method) && StringUtils.isNotEmpty(payload)) {
                    create(aTarget, payload);
                }

                // Existing annotation deleted
                if ("DELETE".equals(method) && StringUtils.isNotEmpty(payload)) {
                    delete(aTarget, payload);
                }

                // Existing annotation deleted
                if ("HEAD".equals(method) && StringUtils.isNotEmpty(payload)) {
                    select(aTarget, payload);
                }
            }
            catch (Exception e) {
                error("Error: " + e.getMessage());
                LOG.error("Error: " + e.getMessage(), e);
            }
        }
        
        private void select(AjaxRequestTarget aTarget, String payload)
            throws JsonParseException, JsonMappingException, IOException
        {
            Annotation anno = JSONUtil.getObjectMapper().readValue(payload,
                    Annotation.class);
            if (anno.getRanges().isEmpty()) {
                // Spurious creation event that is to be ignored.
                return;
            }
            
            VID paramId = VID.parse(anno.getId());
            
            try {
                CAS cas = getCasProvider().get();
                
                if (paramId.isSynthetic()) {
                    extensionRegistry.fireAction(getActionHandler(), getModelObject(), aTarget,
                            cas, paramId, "spanOpenDialog");
                    return;
                }

                AnnotationFS fs = selectByAddr(cas, AnnotationFS.class, paramId.getId());
                if (fs.getBegin() > -1 && fs.getEnd() > -1) {
                    AnnotatorState state = getModelObject();
                    if (state.isSlotArmed()) {
                        // When filling a slot, the current selection is *NOT* changed. The
                        // Span annotation which owns the slot that is being filled remains
                        // selected!
                        getActionHandler().actionFillSlot(aTarget, cas, fs.getBegin(),
                                fs.getEnd(), paramId);
                    }
                    else {
                        state.getSelection().selectSpan(paramId, cas, fs.getBegin(),
                                fs.getEnd());
                        getActionHandler().actionSelect(aTarget);
                    }
                }
                else {
                    handleError("Unable to select span annotation: No match was found", aTarget);
                }
            }
            catch (AnnotationException | IOException e) {
                handleError("Unable to select span annotation", e, aTarget);
            }
        }

        private void create(AjaxRequestTarget aTarget, String payload)
            throws JsonParseException, JsonMappingException, IOException
        {
            Annotation anno = JSONUtil.getObjectMapper().readValue(payload,
                    Annotation.class);

            if (anno.getRanges().isEmpty()) {
                // Spurious creation event that is to be ignored.
                return;
            }

            // Since we cannot pass the JSON directly to AnnotatorJS, we attach it to the HTML
            // element into which AnnotatorJS governs. In our modified annotator-full.js, we pick it
            // up from there and then pass it on to AnnotatorJS to do the rendering.
            // String json = toJson(anno);
            // aTarget.prependJavaScript("Wicket.$('" + vis.getMarkupId() + "').temp = " + json + ";");

            try {
                CAS cas = getCasProvider().get();
                int begin = anno.getRanges().get(0).getStartOffset();
                int end = anno.getRanges().get(0).getEndOffset();
                AnnotatorState state = getModelObject();
                if (begin > -1 && end > -1) {
                    if (state.isSlotArmed()) {
                        // When filling a slot, the current selection is *NOT* changed. The
                        // Span annotation which owns the slot that is being filled remains
                        // selected!
                        getActionHandler().actionFillSlot(aTarget, cas, begin,
                                end, NONE_ID);
                    }
                    else {
                        state.getSelection().selectSpan(cas, begin, end);
                        getActionHandler().actionCreateOrUpdate(aTarget, cas);
                    }
                }
                else {
                    handleError("Unable to create span annotation: No match was found", aTarget);
                }
            }
            catch (IOException | AnnotationException e) {
                handleError("Unable to create span annotation", e, aTarget);
            }
        }

        private void delete(AjaxRequestTarget aTarget, String aPayload)
        {
            // We delete annotations via the detail sidebar, so this method is no needed.
        }

        private void update(AjaxRequestTarget aTarget, String aPayload)
        {
            // We update annotations via the detail sidebar, so this method is no needed.
        }

        private void read(AjaxRequestTarget aTarget)
            throws JsonParseException, JsonMappingException, IOException
        {
            CAS cas = getCasProvider().get();

            VDocument vdoc = new VDocument();
            preRenderer.render(vdoc, 0, cas.getDocumentText().length(), cas, getLayersToRender());

            List<Annotation> annotations = new ArrayList<>();

            AnnotatorState state = getModelObject();

            // Render visible (custom) layers
            Map<String[], Queue<String>> colorQueues = new HashMap<>();
            for (AnnotationLayer layer : vdoc.getAnnotationLayers()) {
                ColoringStrategy coloringStrategy = coloringService.getStrategy(layer,
                        state.getPreferences(), colorQueues);
                LOG.info("Annotation Layers");
                LOG.info(layer.getName());
                LOG.info(layer.getId().toString());
                LOG.info(state.getPreferences().getColorPerLayer().toString());
                // If the layer is not included in the rendering, then we skip here - but only after
                // we have obtained a coloring strategy for this layer and thus secured the layer
                // color. This ensures that the layer colors do not change depending on the number
                // of visible layers.
                if (!vdoc.getAnnotationLayers().contains(layer)) {
                    continue;
                }
                
                TypeAdapter typeAdapter = annotationService.getAdapter(layer);

                ColoringRules coloringRules = typeAdapter.getTraits(ColoringRulesTrait.class)
                        .map(ColoringRulesTrait::getColoringRules).orElse(null);

                for (VSpan vspan : vdoc.spans(layer.getId())) {
                    String labelText = getUiLabelText(typeAdapter, vspan);
                    String color = coloringStrategy.getColor(vspan, labelText, coloringRules);

                    Annotation anno = new Annotation();
                    anno.setId(vspan.getVid().toString());
                    anno.setText(labelText);
                    anno.setColor(color);
                    // Looks like the "quote" is not really required for AnnotatorJS to render the
                    // annotation.
                    anno.setQuote("");
                    anno.setRanges(toRanges(vspan.getRanges()));
                    annotations.add(anno);
                }
            }

            String json = toJson(annotations);
            // Since we cannot pass the JSON directly to AnnotatorJS, we attach it to the HTML
            // element into which AnnotatorJS governs. In our modified annotator-full.js, we pick it
            // up from there and then pass it on to AnnotatorJS to do the rendering.
            aTarget.prependJavaScript("Wicket.$('" + textRight.getMarkupId() + "').temp = " + json + ";");
        }

        private List<Range> toRanges(List<VRange> aRanges)
        {
            return aRanges.stream().map(r -> new Range(r.getBegin(), r.getEnd()))
                    .collect(Collectors.toList());
        }

        private List<AnnotationLayer> getLayersToRender()
        {
            AnnotatorState state = getModelObject();
            List<AnnotationLayer> layersToRender = new ArrayList<>();
            for (AnnotationLayer layer : state.getAnnotationLayers()) {
                boolean isSegmentationLayer = layer.getName().equals(Token.class.getName())
                        || layer.getName().equals(Sentence.class.getName());
                boolean isUnsupportedLayer = layer.getType().equals(CHAIN_TYPE)
                        && (state.getMode().equals(Mode.AUTOMATION)
                                || state.getMode().equals(Mode.CORRECTION)
                                || state.getMode().equals(Mode.CURATION));

                if (layer.isEnabled() && !isSegmentationLayer && !isUnsupportedLayer) {
                    layersToRender.add(layer);
                }
            }
            return layersToRender;
        }
    }

    private static class Node
    {
        int position;
        String type;
    }
}
