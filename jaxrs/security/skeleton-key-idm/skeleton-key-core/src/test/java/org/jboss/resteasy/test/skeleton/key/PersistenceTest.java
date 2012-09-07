package org.jboss.resteasy.test.skeleton.key;

import junit.framework.Assert;
import org.infinispan.Cache;
import org.infinispan.manager.DefaultCacheManager;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.skeleton.key.client.SkeletonKeyAdminClient;
import org.jboss.resteasy.skeleton.key.client.SkeletonKeyClientBuilder;
import org.jboss.resteasy.skeleton.key.keystone.model.Project;
import org.jboss.resteasy.skeleton.key.keystone.model.Projects;
import org.jboss.resteasy.skeleton.key.keystone.model.Role;
import org.jboss.resteasy.skeleton.key.keystone.model.StoredUser;
import org.jboss.resteasy.skeleton.key.keystone.model.User;
import org.jboss.resteasy.skeleton.key.server.Loader;
import org.jboss.resteasy.skeleton.key.server.SkeletonKeyApplication;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.test.EmbeddedContainer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.jboss.resteasy.test.TestPortProvider.generateBaseUrl;
import static org.jboss.resteasy.test.TestPortProvider.generateURL;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class PersistenceTest
{
   private static ResteasyDeployment deployment;
   private static SkeletonKeyApplication app;

   public static void init() throws Exception
   {

      StoredUser admin = new StoredUser();
      admin.setName("Bill");
      admin.setUsername("wburke");
      HashMap<String, String> creds = new HashMap<String, String>();
      creds.put("password", "geheim");
      admin.setCredentials(creds);
      app.getUsers().create(admin);

      Project project = new Project();
      project.setName("Skeleton Key");
      project.setEnabled(true);
      app.getProjects().createProject(project);

      Role adminRole = new Role();
      adminRole.setName("admin");
      app.getRoles().create(adminRole);

      app.getProjects().addUserRole(project.getId(), admin.getId(), adminRole.getId());

      // Test export/import
      System.out.println(new Loader().export(app.getCache()));

      try
      {
         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         new Loader().export(app.getCache(), baos);
         ByteArrayInputStream bios = new ByteArrayInputStream(baos.toByteArray());
         app.getCache().clear();
         new Loader().importStore(bios, app.getCache());
      }
      catch (Exception e)
      {
      }
   }

   private static void stopDeployment() throws Exception
   {
      app.getCache().stop();
      deployment = null;
      app = null;
      EmbeddedContainer.stop();
   }

   private static void startDeployment() throws Exception
   {
      deployment = new ResteasyDeployment();
      deployment.setSecurityEnabled(true);
      deployment.setApplicationClass(SkeletonKeyApplication.class.getName());
      ResteasyProviderFactory factory = new ResteasyProviderFactory();
      deployment.setProviderFactory(factory);
      factory.setProperty(SkeletonKeyApplication.SKELETON_KEY_INFINISPAN_CONFIG_FILE, "cache.xml");
      factory.setProperty(SkeletonKeyApplication.SKELETON_KEY_INFINISPAN_CACHE_NAME, "identity-store");

      EmbeddedContainer.start(deployment);
      app = (SkeletonKeyApplication)deployment.getApplication();
   }

   @Test
   public void testAppLoad() throws Exception
   {
      clearCache();
      startDeployment();
      init();
      stopDeployment();
      startDeployment();
      ResteasyClient client = new ResteasyClient();
      WebTarget target = client.target(generateBaseUrl());
      SkeletonKeyAdminClient admin = new SkeletonKeyClientBuilder().username("wburke").password("geheim").idp(target).admin();

      StoredUser newUser = new StoredUser();
      newUser.setName("John Smith");
      newUser.setUsername("jsmith");
      newUser.setEnabled(true);
      Map creds = new HashMap();
      creds.put("password", "foobar");
      newUser.setCredentials(creds);
      Response response = admin.users().create(newUser);
      User user = response.readEntity(User.class);
      response = admin.roles().create("user");
      Role role = response.readEntity(Role.class);
      Projects projects = admin.projects().query("Skeleton Key");
      Project project = projects.getList().get(0);
      admin.projects().addUserRole(project.getId(), user.getId(), role.getId());

      admin = new SkeletonKeyClientBuilder().username("jsmith").password("foobar").idp(target).admin();
      response = admin.roles().create("error");
      Assert.assertEquals(401, response.getStatus());
      stopDeployment();
   }

   @Test
   public void testImportExport() throws Exception
   {
      clearCache();
      startDeployment();
      Assert.assertEquals(0, app.getCache().size());
      init();
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      new Loader().export(app.getCache(), baos);
      app.getCache().clear();
      stopDeployment();

      startDeployment();
      Assert.assertEquals(0, app.getCache().size());
      ByteArrayInputStream bios = new ByteArrayInputStream(baos.toByteArray());
      new Loader().importStore(bios, app.getCache());
      stopDeployment();
      startDeployment();
      Assert.assertTrue(0 < app.getCache().size());


      ResteasyClient client = new ResteasyClient();
      WebTarget target = client.target(generateBaseUrl());
      SkeletonKeyAdminClient admin = new SkeletonKeyClientBuilder().username("wburke").password("geheim").idp(target).admin();

      StoredUser newUser = new StoredUser();
      newUser.setName("John Smith");
      newUser.setUsername("jsmith");
      newUser.setEnabled(true);
      Map creds = new HashMap();
      creds.put("password", "foobar");
      newUser.setCredentials(creds);
      Response response = admin.users().create(newUser);
      User user = response.readEntity(User.class);
      response = admin.roles().create("user");
      Role role = response.readEntity(Role.class);
      Projects projects = admin.projects().query("Skeleton Key");
      Project project = projects.getList().get(0);
      admin.projects().addUserRole(project.getId(), user.getId(), role.getId());

      admin = new SkeletonKeyClientBuilder().username("jsmith").password("foobar").idp(target).admin();
      response = admin.roles().create("error");
      Assert.assertEquals(401, response.getStatus());
      stopDeployment();
   }


   private void clearCache() throws IOException
   {
      Cache<Object,Object> cache = new DefaultCacheManager("cache.xml").getCache("identity-store");
      cache.clear();
      cache.stop();
   }

}
