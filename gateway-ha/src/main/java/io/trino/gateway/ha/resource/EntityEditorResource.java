package io.trino.gateway.ha.resource;

import static io.trino.gateway.ha.router.ResourceGroupsManager.ResourceGroupsDetail;
import static io.trino.gateway.ha.router.ResourceGroupsManager.SelectorsDetail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import io.dropwizard.views.common.View;
import io.trino.gateway.ha.config.ProxyBackendConfiguration;
import io.trino.gateway.ha.router.GatewayBackendManager;
import io.trino.gateway.ha.router.ResourceGroupsManager;
import io.trino.gateway.ha.router.RoutingManager;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RolesAllowed({"ADMIN"})
@Path("entity")
public class EntityEditorResource {

  public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  @Inject
  private GatewayBackendManager gatewayBackendManager;
  @Inject
  private ResourceGroupsManager resourceGroupsManager;

  @Inject
  private RoutingManager routingManager;

  @GET
  @Produces(MediaType.TEXT_HTML)
  public EntityView entityUi(@Context SecurityContext securityContext) {
    return new EntityView("/template/entity-view.ftl", securityContext);
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public List<EntityType> getAllEntityTypes() {
    return Arrays.asList(EntityType.values());
  }

  @POST
  public Response updateEntity(@QueryParam("entityType") String entityTypeStr,
                               @QueryParam("useSchema") String database,
                               String jsonPayload) {
    if (Strings.isNullOrEmpty(entityTypeStr)) {
      throw new WebApplicationException("EntryType can not be null");
    }
    EntityType entityType = EntityType.valueOf(entityTypeStr);
    try {
      switch (entityType) {
        case GATEWAY_BACKEND:
          //TODO: make the gateway backend database sensitive
          ProxyBackendConfiguration backend =
              OBJECT_MAPPER.readValue(jsonPayload, ProxyBackendConfiguration.class);
          gatewayBackendManager.updateBackend(backend);
          log.info("Setting up the backend {} with healthy state", backend.getName());
          routingManager.upateBackEndHealth(backend.getName(), true);
          break;
        case RESOURCE_GROUP:
          ResourceGroupsDetail resourceGroupDetails = OBJECT_MAPPER.readValue(jsonPayload,
              ResourceGroupsDetail.class);
          resourceGroupsManager.updateResourceGroup(resourceGroupDetails, database);
          break;
        case SELECTOR:
          SelectorsDetail selectorDetails = OBJECT_MAPPER.readValue(jsonPayload,
              SelectorsDetail.class);
          List<SelectorsDetail> oldSelectorDetails =
              resourceGroupsManager.readSelector(selectorDetails.getResourceGroupId(), database);
          if (oldSelectorDetails.size() >= 1) {
            resourceGroupsManager.updateSelector(oldSelectorDetails.get(0),
                selectorDetails, database);
          } else {
            resourceGroupsManager.createSelector(selectorDetails, database);
          }
          break;
        default:
      }
    } catch (IOException e) {
      log.error(e.getMessage(), e);
      throw new WebApplicationException(e);
    }
    return Response.ok().build();
  }

  @GET
  @Path("/{entityType}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getAllEntitiesForType(@PathParam("entityType") String entityTypeStr,
                                        @QueryParam("useSchema") String database) {
    EntityType entityType = EntityType.valueOf(entityTypeStr);

    switch (entityType) {
      case GATEWAY_BACKEND:
        return Response.ok(gatewayBackendManager.getAllBackends()).build();
      case RESOURCE_GROUP:
        return Response.ok(resourceGroupsManager.readAllResourceGroups(database)).build();
      case SELECTOR:
        return Response.ok(resourceGroupsManager.readAllSelectors(database)).build();
      default:
    }
    return Response.ok(ImmutableList.of()).build();
  }

  @Data
  public static class EntityView extends View {
    private String displayName;

    protected EntityView(String templateName, SecurityContext securityContext) {
      super(templateName, Charset.defaultCharset());
      setDisplayName(securityContext.getUserPrincipal().getName());
    }
  }
}
