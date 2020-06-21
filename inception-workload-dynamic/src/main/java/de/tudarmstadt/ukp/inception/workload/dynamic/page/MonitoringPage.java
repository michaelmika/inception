/*
 * Copyright 2020
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package de.tudarmstadt.ukp.inception.workload.dynamic.page;

import static de.tudarmstadt.ukp.clarin.webanno.api.WebAnnoConst.PAGE_PARAM_PROJECT_ID;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.persistence.NoResultException;

import org.apache.wicket.ajax.AjaxEventBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormChoiceComponentUpdatingBehavior;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.form.OnChangeAjaxBehavior;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.ajax.markup.html.form.AjaxCheckBox;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.extensions.markup.html.repeater.data.table.DataTable;
import org.apache.wicket.extensions.markup.html.repeater.data.table.HeadersToolbar;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.LambdaColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.NavigationToolbar;
import org.apache.wicket.extensions.markup.html.repeater.data.table.PropertyColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.filter.FilterForm;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.NumberTextField;
import org.apache.wicket.markup.html.form.RadioChoice;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.EmptyPanel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.request.resource.PackageResourceReference;
import org.apache.wicket.request.resource.ResourceReference;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.apache.wicket.util.string.StringValue;
import org.wicketstuff.annotation.mount.MountPath;

import com.googlecode.wicket.kendo.ui.form.datetime.AjaxDatePicker;

import de.tudarmstadt.ukp.clarin.webanno.api.DocumentService;
import de.tudarmstadt.ukp.clarin.webanno.api.ProjectService;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationDocument;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationDocumentState;
import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;
import de.tudarmstadt.ukp.clarin.webanno.security.UserDao;
import de.tudarmstadt.ukp.clarin.webanno.security.model.User;
import de.tudarmstadt.ukp.clarin.webanno.ui.core.menu.MenuItemRegistry;
import de.tudarmstadt.ukp.clarin.webanno.ui.core.page.ApplicationPageBase;
import de.tudarmstadt.ukp.inception.workload.dynamic.support.DataProvider;
import de.tudarmstadt.ukp.inception.workload.dynamic.support.Filter;
import de.tudarmstadt.ukp.inception.workload.dynamic.support.ImagePanel;
import de.tudarmstadt.ukp.inception.workload.dynamic.support.ModalPanel;


@MountPath("/workload.html")
public class MonitoringPage extends ApplicationPageBase
{

    private static final long serialVersionUID = 1180618893870240262L;

    //The Icons
    private static final ResourceReference META =
        new PackageResourceReference(MonitoringPage.class, "book_open.png");

    //Top Form
    Form workload;

    //Default Annotations value
    private IModel<Integer> defaultAnnotations = new Model<>();

    //Current Project
    private IModel<Project> currentProject = new Model<>();

    //Datatable
    private DataTable table;

    //SpringBeans
    private @SpringBean UserDao userRepository;
    private @SpringBean ProjectService projectService;
    private @SpringBean MenuItemRegistry menuItemService;
    private @SpringBean DocumentService documentService;


    //Default constructor, no project selected (only when workload.html
    // put directly in the browser without any parameters)
    public MonitoringPage() {
        super();
        //Error, user is returned to home page, nothing else to do
        error("No Project selected, please enter the monitoring page only with a valid project reference");
        setResponsePage(getApplication().getHomePage());

    }



    //Constructor with a project
    public MonitoringPage(final PageParameters aPageParameters)
    {
        super(aPageParameters);

        //top form initialize
        workload = new Form("workload");
        workload.setOutputMarkupId(true);

        //Get current Project
        StringValue projectParameter = aPageParameters.get(PAGE_PARAM_PROJECT_ID);
        if (getProjectFromParameters(projectParameter).isPresent())
        {
            currentProject.setObject(getProjectFromParameters(projectParameter).get());
        } else {
            currentProject = null;
        }


        //Get the current user
        User user = userRepository.getCurrentUser();
        //Check if Project exists
        if (currentProject != null)
        {




            //Check if user is allowed to see the monitoring page of this project
            if (currentProject != null &&
                !(projectService.isCurator(currentProject.getObject(), user)
                || projectService.isManager(currentProject.getObject(), user)))
            {
                //Required even in case of error due to wicket
                workload.add(new ModalWindow("modalWindow"));
                workload.add(new NumberTextField("defaultDocumentsNumberTextField"));
                workload.add(new EmptyPanel("dataTable"));
                error("You have no permission to access project [" + currentProject.getObject().getId() + "]");
                setResponsePage(getApplication().getHomePage());

            } else {
                initialize();
            }

        } else {
            //Project does not exists, returning to homepage
            error("Project [" + projectParameter + "] does not exist");
            setResponsePage(getApplication().getHomePage());

        }

    }

    public void initialize()
    {

        //Header of the page
        Label name = new Label("name",currentProject.getObject().getName());
        add(name);


        //Headers of the table
        List<String> headers = new ArrayList<>();
        headers.add(getString("Document"));
        headers.add(getString("Finished"));
        headers.add(getString("Processed"));
        headers.add(getString("Metadata"));

        //Data Provider for the table
        DataProvider dataProvider = new DataProvider(documentService.
            listSourceDocuments(currentProject.getObject()),
            headers, documentService.listAnnotationDocuments
            (currentProject.getObject()));

        //Init defaultDocumentsNumberTextField
        NumberTextField defaultNumberDocumentsTextField =
            new NumberTextField("defaultDocumentsNumberTextField", Integer.class);

        //Set minimum value for input
        defaultNumberDocumentsTextField.setMinimum(1);
        //After upstream change
        defaultNumberDocumentsTextField.setRequired(true);

        ModalWindow modalWindow = new ModalWindow("modalWindow");
        workload.add(modalWindow);




        //Get value for the project and set it accordingly
        //TODO get correct value after upstream change
        defaultNumberDocumentsTextField.setDefaultModel(new CompoundPropertyModel<Integer>(6));
        defaultNumberDocumentsTextField.setConvertEmptyInputStringToNull(false);

        //add AJAX event handler on changing input value
        defaultNumberDocumentsTextField.add(new OnChangeAjaxBehavior()
        {
            private static final long serialVersionUID = 2607214157084529408L;

            @Override
            protected void onUpdate(AjaxRequestTarget ajaxRequestTarget)
            {
                defaultAnnotations.setObject(Integer.parseInt
                    (defaultNumberDocumentsTextField.getInput()));
            }
        });

        workload.add(defaultNumberDocumentsTextField);

        //Columns of the table
        List<IColumn> columns = new ArrayList<>();

        //Filter properties
        FilterForm<Filter> filter = new FilterForm<>(getString("filter"), dataProvider);
        filter.setOutputMarkupId(true);

        //Filter Textfields and their AJAX events
        TextField<String> userFilterTextField = new TextField("userFilter",
            PropertyModel.of(dataProvider, "filter.username"), String.class);

        userFilterTextField.add(new AjaxFormComponentUpdatingBehavior("onchange") {
            @Override
            protected void onUpdate(AjaxRequestTarget ajaxRequestTarget) {
                ajaxRequestTarget.add(workload);
            }
        });

        TextField<String> documentFilterTextField = new TextField("documentFilter",
                PropertyModel.of(dataProvider, "filter.documentName"), String.class);

        documentFilterTextField.add(new AjaxFormComponentUpdatingBehavior("onchange") {
                @Override
                protected void onUpdate(AjaxRequestTarget ajaxRequestTarget) {
                    ajaxRequestTarget.add(workload);
                }
            });


        workload.add(userFilterTextField);
        workload.add(documentFilterTextField);




        //Each column creates TableMetaData
        columns.add(new LambdaColumn<>(new ResourceModel(getString("Document"))
            , getString("Document"), SourceDocument::getName));
        columns.add(new LambdaColumn<>(new ResourceModel(getString("Finished"))
            , getString("Finished"), aDocument -> dataProvider.getFinishedAmountForDocument
            ((SourceDocument)aDocument)));
        columns.add(new LambdaColumn<>(new ResourceModel(getString("Processed"))
            , getString("Processed"), aDocument -> dataProvider.
            getInProgressAmountForDocument((SourceDocument)aDocument)));

        //Own column type, contains only a clickable image (AJAX event),
        //creates a small panel dialog containing metadata
        columns.add(new PropertyColumn<SourceDocument,String>(new Model(getString("Metadata")),
            getString("Metadata"))
        {
            private static final long serialVersionUID = 1L;
            @Override
            public void populateItem(Item aItem, String aID, IModel aModel)
            {

                //Add the Icon
                ImagePanel icon = new ImagePanel(aID, META);
                icon.add(new AttributeAppender("style", "cursor: pointer", ";"));
                aItem.add(icon);

                //Click event in the metadata column
                aItem.add(new AjaxEventBehavior("click")
                {
                    private static final long serialVersionUID = 7624208971279187266L;

                    @Override
                    protected void onEvent(AjaxRequestTarget aTarget)
                    {


                        //Get the current selected Row
                        Item rowItem = aItem.findParent(Item.class);
                        int rowIndex = rowItem.getIndex();
                        SourceDocument doc = dataProvider.getShownDocuments().
                            get((int)(table.getCurrentPage() * table.getItemsPerPage()) + rowIndex);

                        //Create the modalWindow
                        modalWindow.setTitle("Metadata of document: " + doc.getName());


                        //Set contents of the modalWindow
                        modalWindow.setContent(new ModalPanel(modalWindow.
                            getContentId(), doc, listUsersFinishedForDocument(doc),
                            listUsersInProgressForDocument(doc)));

                        //Open the dialog
                        modalWindow.show(aTarget);
                    }
                });
            }
        });

        //The DataTable
        table = new DataTable("dataTable", columns, dataProvider, 20);
        table.setOutputMarkupId(true);

        //FilterToolbar
        table.addTopToolbar(new NavigationToolbar(table));
        table.addTopToolbar(new HeadersToolbar(table, dataProvider));

        //Add the table to the filter form
        filter.add(table);



        //Checkbox for showing only unused source documents, disables other textfields
        AjaxCheckBox unused = new AjaxCheckBox("unused",
            PropertyModel.of(dataProvider,"filter.selected")) {

            @Override
            protected void onUpdate(AjaxRequestTarget ajaxRequestTarget) {

                if (getDefaultModelObjectAsString().equals("false"))
                {
                    userFilterTextField.setEnabled(true);
                    documentFilterTextField.setEnabled(true);
                } else {
                    userFilterTextField.setModelObject(null);
                    documentFilterTextField.setModelObject(null);
                    userFilterTextField.setEnabled(false);
                    documentFilterTextField.setEnabled(false);
                }
                ajaxRequestTarget.add(userFilterTextField);
                ajaxRequestTarget.add(documentFilterTextField);

            }
        };


        unused.setOutputMarkupId(true);
        workload.add(unused);


        //Input dates
        AjaxDatePicker dateFrom = new AjaxDatePicker("from",
            PropertyModel.of(dataProvider,"filter.from"), "dd/MM/yyyy");
        AjaxDatePicker dateTo = new AjaxDatePicker("to",
            PropertyModel.of(dataProvider,"filter.to"),"dd/MM/yyyy");

        dateFrom.setOutputMarkupId(true);
        dateTo.setOutputMarkupId(true);

        workload.add(dateFrom);
        workload.add(dateTo);


        //Date choices
        List<String> dateChoice = new ArrayList<>();
        dateChoice.add(getString("from"));
        dateChoice.add(getString("until"));
        dateChoice.add(getString("between"));

        //Craete the radio button group
        RadioChoice<String> dateChoices =
            new RadioChoice<>("date"
                , new Model<>(), dateChoice);
        //Set default value for the group
        dateChoices.setModel(new Model<>(getString("between")));

        dateChoices.setOutputMarkupId(true);

        //Update Behaviour on click, disable according date inputs and reset their values
        dateChoices.add(new AjaxFormChoiceComponentUpdatingBehavior() {
            @Override
            protected void onUpdate(AjaxRequestTarget ajaxRequestTarget) {
                if (getComponent().getDefaultModelObjectAsString().equals("from"))
                {
                    dateTo.setModelObject(null);
                    dateTo.setEnabled(false);
                    dateFrom.setEnabled(true);


                } else if (getComponent().getDefaultModelObjectAsString().equals("until"))
                {
                    dateFrom.setModelObject(null);
                    dateFrom.setEnabled(false);
                    dateTo.setEnabled(true);

                } else {
                    dateTo.setEnabled(true);
                    dateFrom.setEnabled(true);
                }
                ajaxRequestTarget.add(dateFrom);
                ajaxRequestTarget.add(dateTo);
            }
        });

        //add them to the form
        workload.add(dateChoices);

        //Submit button
        Button submit = new AjaxButton(getString("Search"), Model.of("Search")) {
            private static final long serialVersionUID = 3521172967850377971L;

            @Override
            protected void onSubmit(AjaxRequestTarget target) {
                target.add(filter);
            }
        };

        workload.add(submit);

        //Reset button
        Button reset = new AjaxButton(getString("Reset"), Model.of("Reset")) {
            @Override
            public void onSubmit(AjaxRequestTarget target) {
                userFilterTextField.setModelObject(null);
                documentFilterTextField.setModelObject(null);
                dateFrom.setModelObject(null);
                dateTo.setModelObject(null);
                unused.setModelObject(null);
                dateChoices.setModel(new Model<>(getString("between")));
                dateFrom.setEnabled(true);
                dateTo.setEnabled(true);

                target.add(userFilterTextField);
                target.add(documentFilterTextField);
                target.add(dateFrom);
                target.add(dateTo);
                target.add(unused);
                target.add(dateChoices);
                target.add(dateFrom);
                target.add(dateTo);
            }
        };

        workload.add(reset);

        //Filter components with the table
        workload.add(filter);

        //Add to the page
        add(workload);

    }


    //--------------------------------------- Helper methods -------------------------------------//


    //Return current project, required for several purposes
    private Optional<Project> getProjectFromParameters(StringValue projectParam)
    {
        if (projectParam == null || projectParam.isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(projectService.getProject(projectParam.toLong()));
        }
        catch (NoResultException e) {
            return Optional.empty();
        }
    }

    //
    public List<String> listUsersFinishedForDocument(SourceDocument aDocument)
    {
        List<String> result = new ArrayList<>();
        for (AnnotationDocument anno: documentService.
            listAnnotationDocuments(currentProject.getObject()))
        {
            if (anno.getState().equals(AnnotationDocumentState.FINISHED)
                && anno.getName().equals(aDocument.getName()))
            {
                result.add(anno.getUser());
            }
        }
        return result;
    }

    //
    public List<String> listUsersInProgressForDocument(SourceDocument aDocument)
    {
        List<String> result = new ArrayList<>();
        for (AnnotationDocument anno: documentService.
            listAnnotationDocuments(currentProject.getObject()))
        {
            if (anno.getState().equals(AnnotationDocumentState.IN_PROGRESS)
                && anno.getName().equals(aDocument.getName()))
            {
                result.add(anno.getUser());
            }
        }
        return result;
    }

}