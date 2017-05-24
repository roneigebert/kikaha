package kikaha.core.modules.security;

import javax.annotation.PostConstruct;
import javax.inject.*;
import kikaha.config.Config;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

/**
 *
 */
@Slf4j
@Getter
@Singleton
@ToString
public class DefaultAuthenticationConfiguration {

	@Inject Config config;

	private String loginPage;
	private String errorPage;
	private String successPage;
	private String callbackUrl;
	private String logoutUrl;
	private String permissionDeniedPage;

	@PostConstruct
	public void readConfiguration(){
		final Config authConfig = this.config.getConfig( "server.auth.endpoints" );
		loginPage = authConfig.getString( "login-page" );
		errorPage = authConfig.getString( "error-page" );
		successPage = authConfig.getString( "success-page" );
		callbackUrl = authConfig.getString( "callback-url" );
		logoutUrl = authConfig.getString( "logout-url" );
		permissionDeniedPage = authConfig.getString( "permission-denied-page" );
	}

	public void logDetailedInformationAboutThisConfig() {
		log.info( "Defined authentication endpoints:" );
		log.info( "  login-page: " + loginPage );
		log.info( "  error-page: " + errorPage );
		log.info( "  success-page: " + successPage );
		log.info( "  callback-url: " + callbackUrl );
		log.info( "  logout-url: " + logoutUrl );
	}
}