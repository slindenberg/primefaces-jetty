package de.stefanlindenberg.web.pfjetty.myproject.web.beans;

import java.io.Serializable;

import javax.faces.application.FacesMessage;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;
import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;
import javax.servlet.ServletContext;

import org.primefaces.context.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.stefanlindenberg.web.pfjetty.StandaloneJetty;

@ManagedBean(name = "testController")
@ViewScoped
public class TestController implements Serializable {

	private static final long serialVersionUID = 1L;

	private transient final Logger log = LoggerFactory.getLogger(this.getClass());

	public TestController() {
		log.info("New " + this.getClass().getSimpleName() + " ...");
	}

	public void buttonAction(ActionEvent actionEvent) {
		FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_INFO, "Welcome to Primefaces!", null);
		FacesContext.getCurrentInstance().addMessage(null, message);
	}

	public void shutdown() {
		ServletContext ctx = (ServletContext) FacesContext.getCurrentInstance().getExternalContext().getContext();
		StandaloneJetty serverInstance = (StandaloneJetty) ctx.getAttribute(StandaloneJetty.SERVER_REFERENCE);
		serverInstance.shutdown();
		FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_INFO, "Server shutdown", "The server is shutting down ... Watch the tray icon disappear.");
		RequestContext.getCurrentInstance().showMessageInDialog(message);
	}
}
