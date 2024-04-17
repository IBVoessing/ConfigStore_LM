package com.voessing.vcde.endpoints.vrh.resources;

import java.io.IOException;

import com.voessing.common.TNotesUtil;
import com.voessing.vcde.endpoints.vrh.resources.fromview.PmProjectConfigsOfProject;
import com.voessing.vcde.endpoints.vrh.resources.fromview.PmProjectParticipantsOfProject;
import com.voessing.vcde.endpoints.vrh.resources.fromview.PmProjects;
import com.voessing.vcde.endpoints.vrh.resources.fromview.PmRequestsForTiOfProject;
import com.voessing.vcde.endpoints.vrh.resources.fromview.PmToolInstanceMembershipsOfProject;
import com.voessing.vcde.endpoints.vrh.resources.fromview.PmToolInstancesOfProject;
import com.voessing.vcde.endpoints.vrh.resources.fromview.PmTools;
import com.voessing.xapps.utils.vrh.exceptions.VrhException;
import com.voessing.xapps.utils.vrh.router.VrhSimpleRouter;

public class VCDERequestHandler {

	public static void handleRequest() {
		
		try {
			
			//System.out.println("Called");
			
			new VrhSimpleRouter<>()
				// define routes
				.addRoute("/configuration", Configuration.class)
				.addRoute("/currentuser", CurrentUser.class)			
				.addRoute("/pm-projectparticipants", PmProjectParticipantsOfProject.class)
				.addRoute("/pm-projectconfigurations", PmProjectConfigsOfProject.class)			
				.addRoute("/pm-projects", PmProjects.class)
				.addRoute("/pm-toolinstancememberships", PmToolInstanceMembershipsOfProject.class)
				.addRoute("/pm-toolinstances", PmToolInstancesOfProject.class)
				.addRoute("/pm-tools", PmTools.class)
				.addRoute("/pm-requestsforti", PmRequestsForTiOfProject.class)
				
				// EXAM;PLE: delegate unanswered request to other router			
	//			.addRouter(new VrhSimpleRouter<>()
	//				.addRoute("/importall", ImportAll.class)
	//			)
				
				// start handling the request
				.handleRequest();
		} catch (Exception e) {
			TNotesUtil.stdErrorHandler(e);
		}
	}

}