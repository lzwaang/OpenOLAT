/**
 * <a href="http://www.openolat.org">
 * OpenOLAT - Online Learning and Training</a><br>
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); <br>
 * you may not use this file except in compliance with the License.<br>
 * You may obtain a copy of the License at the
 * <a href="http://www.apache.org/licenses/LICENSE-2.0">Apache homepage</a>
 * <p>
 * Unless required by applicable law or agreed to in writing,<br>
 * software distributed under the License is distributed on an "AS IS" BASIS, <br>
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. <br>
 * See the License for the specific language governing permissions and <br>
 * limitations under the License.
 * <p>
 * Initial code contributed and copyrighted by<br>
 * frentix GmbH, http://www.frentix.com
 * <p>
 */
package org.olat.course.nodes.opencast.ui;

import static org.olat.core.gui.translator.TranslatorHelper.translateAll;

import org.olat.core.gui.UserRequest;
import org.olat.core.gui.components.form.flexible.FormItem;
import org.olat.core.gui.components.form.flexible.FormItemContainer;
import org.olat.core.gui.components.form.flexible.elements.AutoCompleter;
import org.olat.core.gui.components.form.flexible.elements.SingleSelection;
import org.olat.core.gui.components.form.flexible.elements.StaticTextElement;
import org.olat.core.gui.components.form.flexible.impl.FormBasicController;
import org.olat.core.gui.components.form.flexible.impl.FormEvent;
import org.olat.core.gui.components.form.flexible.impl.FormLayoutContainer;
import org.olat.core.gui.components.form.flexible.impl.elements.AutoCompleteFormEvent;
import org.olat.core.gui.control.Controller;
import org.olat.core.gui.control.WindowControl;
import org.olat.core.util.StringHelper;
import org.olat.course.editor.NodeEditController;
import org.olat.course.nodes.OpencastCourseNode;
import org.olat.modules.ModuleConfiguration;
import org.olat.modules.opencast.AuthDelegate;
import org.olat.modules.opencast.OpencastEventProvider;
import org.olat.modules.opencast.OpencastSeriesProvider;
import org.olat.modules.opencast.OpencastService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 
 * Initial date: 11 Aug 2020<br>
 * @author uhensler, urs.hensler@frentix.com, http://www.frentix.com
 *
 */
public class OpencastConfigController extends FormBasicController {
	
	private static final String DISPLAY_KEY_SERIES = "config.display.series";
	private static final String DISPLAY_KEY_EVENT = "config.display.event";
	private static final String[] DISPLAY_KEYS = new String[] {
			DISPLAY_KEY_SERIES,
			DISPLAY_KEY_EVENT
	};
	private static final String MORE_KEY = ".....";
	
	private SingleSelection displayEl;
	private AutoCompleter seriesEl;
	private AutoCompleter eventEl;
	private StaticTextElement identifierEl;
	
	private final ModuleConfiguration config;
	
	@Autowired
	private OpencastService opncastService;
	
	public OpencastConfigController(UserRequest ureq, WindowControl wControl, OpencastCourseNode courseNode) {
		super(ureq, wControl);
		config = courseNode.getModuleConfiguration();
		
		initForm(ureq);
		updateUI();
	}

	@Override
	protected void initForm(FormItemContainer formLayout, Controller listener, UserRequest ureq) {
		setFormTitle("pane.tab.config");
		setFormContextHelp("Knowledge Transfer#_opencast");
		setFormTranslatedDescription(getFormDescription());
		
		displayEl = uifactory.addRadiosVertical("config.display", formLayout, DISPLAY_KEYS, translateAll(getTranslator(), DISPLAY_KEYS));
		displayEl.addActionListener(FormEvent.ONCHANGE);
		String selectedKey = config.has(OpencastCourseNode.CONFIG_EVENT_IDENTIFIER)? DISPLAY_KEY_EVENT: DISPLAY_KEY_SERIES;
		displayEl.select(selectedKey, true);
		
		String seriesIdentifier = config.getStringValue(OpencastCourseNode.CONFIG_SERIES_IDENTIFIER, null);
		String seriesTitle = null;
		if (seriesIdentifier != null) {
			seriesTitle = config.getStringValue(OpencastCourseNode.CONFIG_TITLE);
		}
		seriesEl = uifactory.addTextElementWithAutoCompleter("config.series", "config.series", 128, seriesTitle, formLayout);
		seriesEl.setListProvider(new OpencastSeriesProvider(getIdentity(), MORE_KEY), ureq.getUserSession());
		seriesEl.setKey(seriesIdentifier);
		seriesEl.setMinLength(1);
		
		String eventIdentifier = config.getStringValue(OpencastCourseNode.CONFIG_EVENT_IDENTIFIER, null);
		String eventTitle = null;
		if (eventIdentifier != null) {
			eventTitle = config.getStringValue(OpencastCourseNode.CONFIG_TITLE);
		}
		eventEl = uifactory.addTextElementWithAutoCompleter("config.event", "config.event", 128, eventTitle, formLayout);
		eventEl.setListProvider(new OpencastEventProvider(getIdentity(), MORE_KEY), ureq.getUserSession());
		eventEl.setKey(eventIdentifier);
		eventEl.setMinLength(1);
		
		String identifier = null;
		if (StringHelper.containsNonWhitespace(seriesIdentifier)) {
			identifier = seriesIdentifier;
		} else if (StringHelper.containsNonWhitespace(eventIdentifier)) {
			identifier = eventIdentifier;
		}
		identifierEl = uifactory.addStaticTextElement("config.identifier", identifier, formLayout);
		
		FormLayoutContainer buttonsCont = FormLayoutContainer.createButtonLayout("buttons", getTranslator());
		formLayout.add(buttonsCont);
		uifactory.addFormSubmitButton("save", buttonsCont);
	}

	private String getFormDescription() {
		StringBuilder sb = new StringBuilder();
		sb.append(translate("config.desc.select"));
		AuthDelegate authDelegate = opncastService.getAuthDelegate(getIdentity());
		if (AuthDelegate.Type.User == authDelegate.getType()) {
			sb.append(" ").append(translate("config.desc.user", new String[] {authDelegate.getValue()}));
		} else if (AuthDelegate.Type.Roles == authDelegate.getType()) {
			sb.append(" ").append(translate("config.desc.roles", new String[] {authDelegate.getValue()}));
		}
		return sb.toString();
	}
	
	private void updateUI() {
		boolean seriesSelected = displayEl.isOneSelected() && displayEl.getSelectedKey().equals(DISPLAY_KEY_SERIES);
		seriesEl.setVisible(seriesSelected);
		eventEl.setVisible(!seriesSelected);
	}

	@Override
	protected void formInnerEvent(UserRequest ureq, FormItem source, FormEvent event) {
		if (source == displayEl) {
			identifierEl.setValue("");
			updateUI();
		} else if (source == seriesEl || source == eventEl) {
			if (event instanceof AutoCompleteFormEvent) {
				String key = ((AutoCompleteFormEvent)event).getKey();
				if (!MORE_KEY.equals(key)) {
					identifierEl.setValue(key);
				}
			}
		}
		super.formInnerEvent(ureq, source, event);
	}

	@Override
	protected boolean validateFormLogic(UserRequest ureq) {
		boolean allOk = super.validateFormLogic(ureq);
		
		seriesEl.clearError();
		eventEl.clearError();
		boolean seriesSelected = displayEl.isOneSelected() && displayEl.getSelectedKey().equals(DISPLAY_KEY_SERIES);
		if (seriesSelected) {
			if (!StringHelper.containsNonWhitespace(seriesEl.getValue())) {
				seriesEl.setErrorKey("form.legende.mandatory", null);
				allOk &= false;
			}
		} else {
			if (!StringHelper.containsNonWhitespace(eventEl.getValue())) {
				eventEl.setErrorKey("form.legende.mandatory", null);
				allOk &= false;
			}
		}
		
		return allOk;
	}

	@Override
	protected void formOK(UserRequest ureq) {
		boolean seriesSelected = displayEl.isOneSelected() && displayEl.getSelectedKey().equals(DISPLAY_KEY_SERIES);
		if (seriesSelected) {
			String seriesIdentifier = seriesEl.getKey();
			config.setStringValue(OpencastCourseNode.CONFIG_SERIES_IDENTIFIER, seriesIdentifier);
			String title = seriesEl.getValue();
			config.setStringValue(OpencastCourseNode.CONFIG_TITLE, title);
			config.remove(OpencastCourseNode.CONFIG_EVENT_IDENTIFIER);
		} else {
			String eventIdentifier = eventEl.getKey();
			config.setStringValue(OpencastCourseNode.CONFIG_EVENT_IDENTIFIER, eventIdentifier);
			String title = eventEl.getValue();
			config.setStringValue(OpencastCourseNode.CONFIG_TITLE, title);
			config.remove(OpencastCourseNode.CONFIG_SERIES_IDENTIFIER);
		}
		
		fireEvent(ureq, NodeEditController.NODECONFIG_CHANGED_EVENT);
	}

	@Override
	protected void doDispose() {
		//
	}

}
