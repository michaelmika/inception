/*
 * Copyright 2019
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
package de.tudarmstadt.ukp.inception.revieweditor;

import java.io.IOException;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.uima.cas.CAS;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.feedback.IFeedback;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.model.IModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wicketstuff.event.annotation.OnEvent;

import de.tudarmstadt.ukp.clarin.webanno.api.CasProvider;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.AnnotationEditorBase;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.action.AnnotationActionHandler;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.exception.AnnotationException;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.AnnotatorState;


public class ReviewEditor
    extends AnnotationEditorBase
{
    private static final long serialVersionUID = 7926530989189120097L;
    private static final Logger LOG = LoggerFactory.getLogger(ReviewEditor.class);

    public ReviewEditor(String aId, IModel<AnnotatorState> aModel,
            AnnotationActionHandler aActionHandler, CasProvider aCasProvider)
    {
        super(aId, aModel, aActionHandler, aCasProvider);
    
        add(new StructuredReviewDraftPanel("vis", aModel, aCasProvider));
    }

    @Override
    public void renderHead(IHeaderResponse aResponse)
    {
        super.renderHead(aResponse);
    }

    @Override
    public void render(AjaxRequestTarget aTarget)
    {
        aTarget.add(this);
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
    
    @OnEvent(stop = true)
    public void onLinkFeatureSetEvent(SelectAnnotationEvent aEvent)
    {
        try {
            CAS cas = getCasProvider().get();
            getModelObject().getSelection().selectSpan(
                aEvent.getVid(), cas, aEvent.getBegin(), aEvent.getEnd());
            getActionHandler().actionSelect(aEvent.getTarget(), cas);
        }
        catch (IOException e) {
            LOG.error("Unable to load CAS", e);
        }
        catch (AnnotationException e) {
            LOG.error("Unable to select annotation", e);
        }
    }

}