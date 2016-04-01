package com.pmease.gitplex.web.component.depotselector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AbstractDefaultAjaxBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.attributes.CallbackParameter;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.head.OnDomReadyHeaderItem;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.IRequestParameters;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.request.resource.CssResourceReference;
import org.apache.wicket.request.resource.JavaScriptResourceReference;

import com.pmease.commons.wicket.assets.hotkeys.HotkeysResourceReference;
import com.pmease.commons.wicket.assets.scrollintoview.ScrollIntoViewResourceReference;
import com.pmease.commons.wicket.behavior.FormComponentInputBehavior;
import com.pmease.gitplex.core.GitPlex;
import com.pmease.gitplex.core.entity.Depot;
import com.pmease.gitplex.core.manager.DepotManager;
import com.pmease.gitplex.web.WebSession;
import com.pmease.gitplex.web.page.depot.file.DepotFilePage;

@SuppressWarnings("serial")
public abstract class DepotSelector extends Panel {

	private final IModel<Collection<Depot>> depotsModel;
	
	private final Long currentDepotId;

	private ListView<Depot> depotsView;
	
	public DepotSelector(String id, IModel<Collection<Depot>> depotsModel, Long currentDepotId) {
		super(id);
		
		this.depotsModel = depotsModel;
		this.currentDepotId = currentDepotId;
	}

	@Override
	protected void onInitialize() {
		super.onInitialize();
		
		WebMarkupContainer depotsContainer = new WebMarkupContainer("depots") {

			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(!depotsView.getModelObject().isEmpty());
			}
			
		};
		depotsContainer.setOutputMarkupPlaceholderTag(true);
		add(depotsContainer);
		
		WebMarkupContainer noDepotsContainer = new WebMarkupContainer("noDepots") {

			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(depotsView.getModelObject().isEmpty());
			}
			
		};
		noDepotsContainer.setOutputMarkupPlaceholderTag(true);
		add(noDepotsContainer);
		
		TextField<String> searchField = new TextField<String>("search", Model.of(""));
		add(searchField);
		searchField.add(new FormComponentInputBehavior() {
			
			@Override
			protected void onInput(AjaxRequestTarget target) {
				target.add(depotsContainer);
				target.add(noDepotsContainer);
			}
			
		});
		searchField.add(new AbstractDefaultAjaxBehavior() {

			@Override
			protected void respond(AjaxRequestTarget target) {
				IRequestParameters params = RequestCycle.get().getRequest().getQueryParameters();
				Long id = params.getParameterValue("id").toLong();
				onSelect(target, GitPlex.getInstance(DepotManager.class).load(id));
			}

			@Override
			public void renderHead(Component component, IHeaderResponse response) {
				super.renderHead(component, response);
				
				String script = String.format("gitplex.depotSelector.init('%s', %s)", 
						searchField.getMarkupId(true), 
						getCallbackFunction(CallbackParameter.explicit("id")));
				response.render(OnDomReadyHeaderItem.forScript(script));
			}
			
		});
		
		depotsContainer.add(depotsView = new ListView<Depot>("depots", 
				new LoadableDetachableModel<List<Depot>>() {

			@Override
			protected List<Depot> load() {
				List<Depot> depots = new ArrayList<>();
				for (Depot depot: depotsModel.getObject()) {
					if (depot.matchesFQN(searchField.getInput())) {
						depots.add(depot);
					}
				}
				depots.sort(WebSession.get().getDepotVisits().getComparator());
				return depots;
			}
			
		}) {

			@Override
			protected void populateItem(ListItem<Depot> item) {
				Depot depot = item.getModelObject();
				AjaxLink<Void> link = new AjaxLink<Void>("link") {

					@Override
					public void onClick(AjaxRequestTarget target) {
						onSelect(target, item.getModelObject());
					}
					
					@Override
					protected void onComponentTag(ComponentTag tag) {
						super.onComponentTag(tag);
						
						PageParameters params = DepotFilePage.paramsOf(item.getModelObject());
						tag.put("href", urlFor(DepotFilePage.class, params).toString());
					}
					
				};
				if (depot.getId().equals(currentDepotId)) 
					link.add(AttributeAppender.append("class", " current"));
				String label = depot.getAccount().getName() + " " + Depot.FQN_SEPARATOR + " " + depot.getName();
				link.add(new Label("name", label));
				item.add(link);
				
				if (item.getIndex() == 0)
					item.add(AttributeAppender.append("class", "active"));
				item.add(AttributeAppender.append("data-id", depot.getId()));
			}
			
		});
	}

	@Override
	protected void onDetach() {
		depotsModel.detach();
		
		super.onDetach();
	}

	@Override
	public void renderHead(IHeaderResponse response) {
		super.renderHead(response);
		
		response.render(JavaScriptHeaderItem.forReference(HotkeysResourceReference.INSTANCE));
		response.render(JavaScriptHeaderItem.forReference(ScrollIntoViewResourceReference.INSTANCE));
		
		response.render(JavaScriptHeaderItem.forReference(
				new JavaScriptResourceReference(DepotSelector.class, "depot-selector.js")));
		response.render(CssHeaderItem.forReference(
				new CssResourceReference(DepotSelector.class, "depot-selector.css")));
	}
	
	protected abstract void onSelect(AjaxRequestTarget target, Depot depot);
	
}