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
package org.olat.basesecurity.manager;

import java.util.Calendar;
import java.util.List;

import javax.persistence.TypedQuery;

import org.olat.basesecurity.Authentication;
import org.olat.basesecurity.AuthenticationImpl;
import org.olat.basesecurity.BaseSecurityModule;
import org.olat.basesecurity.IdentityRef;
import org.olat.core.commons.persistence.DB;
import org.olat.ldap.ui.LDAPAuthenticationController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 
 * Initial date: 3 janv. 2017<br>
 * @author srosse, stephane.rosse@frentix.com, http://www.frentix.com
 *
 */
@Service
public class AuthenticationDAO {
	
	@Autowired
	private DB dbInstance;
	
	/**
	 * Quick update of the credential, don't do a full update of the authentication object.
	 * 
	 * @param auth
	 * @param token
	 */
	public void updateCredential(Authentication auth, String token) {
		StringBuilder sb = new StringBuilder(128);
		sb.append("update ").append(AuthenticationImpl.class.getName()).append(" set credential=:token where key=:authKey");
		dbInstance.getCurrentEntityManager()
			.createQuery(sb.toString())
			.setParameter("authKey", auth.getKey())
			.setParameter("token", token)
			.executeUpdate();
		dbInstance.commit();
	}
	
	/**
	 * The query return as valid OLAT authentication fallback for LDAP.
	 * 
	 * @param identity The identity to check
	 * @param changeOnce If the identity need to change its password at least once
	 * @param maxAge The max. age of the authentication in seconds
	 * @return
	 */
	public boolean hasValidOlatAuthentication(IdentityRef identity, boolean changeOnce, int maxAge) {
		StringBuilder sb = new StringBuilder(256);
		sb.append("select auth.key from ").append(AuthenticationImpl.class.getName()).append(" as auth")
		  .append(" where auth.identity.key=:identityKey and ((auth.provider=:olatProvider");
		
		if(changeOnce) {
			sb.append(" and not(auth.creationDate=auth.lastModified)");
		}
		if(maxAge > 0) {
			sb.append(" and auth.lastModified>=:maxDate");
		}
		sb.append(") or auth.provider=:ldapProvider) ");
		
		TypedQuery<Long> query = dbInstance.getCurrentEntityManager()
			.createQuery(sb.toString(), Long.class)
			.setParameter("identityKey", identity.getKey())
			.setParameter("olatProvider", BaseSecurityModule.getDefaultAuthProviderIdentifier())
			.setParameter("ldapProvider", LDAPAuthenticationController.PROVIDER_LDAP);
		
		if(maxAge > 0) {
			Calendar cal = Calendar.getInstance();
			cal.add(Calendar.SECOND, -maxAge);
			query.setParameter("maxDate", cal.getTime());
		}
		
		List<Long> keys = query
			.setFirstResult(0)
			.setMaxResults(1)
			.getResultList();
		return keys != null && !keys.isEmpty() && keys.get(0) != null;
	}

}
