/*
 *     Copyright 2025 Philterd, LLC @ https://www.philterd.ai
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.philterd.searchredact;

import ai.philterd.phileas.model.enums.MimeType;
import ai.philterd.phileas.model.policy.Policy;
import ai.philterd.phileas.model.responses.FilterResponse;
import ai.philterd.phileas.services.PhileasFilterService;
import ai.philterd.searchredact.ext.SearchRedactParameters;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.ActionFilter;
import org.elasticsearch.action.support.ActionFilterChain;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.tasks.Task;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Map;

public class SearchRedactActionFilter implements ActionFilter {

    private final PhileasFilterService phileasFilterService;

    public SearchRedactActionFilter(final PhileasFilterService phileasFilterService) {
        this.phileasFilterService = phileasFilterService;
    }

    @Override
    public int order() {
        return Integer.MAX_VALUE;
    }

    @Override
    public <Request extends ActionRequest, Response extends ActionResponse> void apply(
            Task task,
            String action,
            Request request,
            ActionListener<Response> listener,
            ActionFilterChain<Request, Response> chain
    ) {

        if (!(request instanceof SearchRequest)) {
            chain.proceed(task, action, request, listener);
            return;
        }

        chain.proceed(task, action, request, new ActionListener<>() {

            @Override
            public void onResponse(Response response) {

                if(request instanceof SearchRequest) {

                    try {

                        response = (Response) handleSearchRequest((SearchRequest) request, response);

                    } catch (Exception ex) {
                        throw new RuntimeException("Unable to apply Phileas to search hit.", ex);
                    }

                }

                listener.onResponse(response);

            }

            @Override
            public void onFailure(Exception ex) {
                listener.onFailure(ex);
            }

        });

    }

    private ActionResponse handleSearchRequest(final SearchRequest searchRequest, final ActionResponse response) throws Exception {

        if (response instanceof SearchResponse) {

            final SearchRedactParameters searchRedactParameters = SearchRedactParameters.getSearchRedactParameters(searchRequest);

            if (searchRedactParameters != null) {

                final String policyJson = searchRedactParameters.getPolicy();
                final String context = searchRedactParameters.getContext();
                final String fieldName = searchRedactParameters.getFieldName();
                final String[] fields = fieldName.split(",");

                // LOGGER.info("policy = {}, context = {}, field = {}", policyJson, context, fieldName);

                final ObjectMapper objectMapper = new ObjectMapper();

                final Policy policy = AccessController.doPrivileged((PrivilegedAction<Policy>) () -> {
                        try {
                            return objectMapper.readValue(policyJson, Policy.class);
                        } catch (JsonProcessingException ex) {
                            throw new RuntimeException(ex);
                        }
                    });

                for (final SearchHit hit : ((SearchResponse) response).getHits().getHits()) {

                    for(final String field : fields) {

                        if (hit.getSourceAsMap().containsKey(field)) {

                            // Look for PII by applying the policy to the selected field.
                            final String input = hit.getSourceAsMap().get(field).toString();

                            final FilterResponse filterResponse = phileasFilterService.filter(policy, context, hit.getId(), input, MimeType.TEXT_PLAIN);

                            final Map<String, Object> sourceMap = hit.getSourceAsMap();
                            sourceMap.put(field, filterResponse.getFilteredText());

                            AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
                                try {
                                    final Map<String, Object> map = new HashMap<>(sourceMap);
                                    hit.sourceRef(new BytesArray(objectMapper.writeValueAsBytes(map)));
                                    return null;
                                } catch (JsonProcessingException e) {
                                    throw new RuntimeException(e);
                                }
                            });

                        } else {
                            //LOGGER.warn("Search request wanted field {} to be redacted but field was not found.", field);
                        }

                    }

                }

            }

        }

        return response;

    }

}
