package com.netflix.exhibitor.core.rest.jersey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.exhibitor.core.entities.IdList;
import com.netflix.exhibitor.core.entities.PathAnalysisNode;
import com.netflix.exhibitor.core.entities.Result;
import com.netflix.exhibitor.core.entities.ServerStatus;
import com.netflix.exhibitor.core.entities.UITabSpec;
import com.netflix.exhibitor.core.entities.UsageListingRequest;
import org.testng.Assert;
import org.testng.annotations.Test;
import java.util.Arrays;

/**
 * The JSON the UI and remote Exhibitor instances see. Expected values were captured from
 * the Jersey 1 "natural" JAXB notation (key order is not significant, so compare as trees).
 */
public class TestEntityJson
{
    private final ObjectMapper mapper = ExhibitorObjectMapperProvider.newMapper();

    private void check(Object entity, String expected) throws Exception
    {
        Assert.assertTrue(mapper.readTree(mapper.writeValueAsString(entity)).equals(mapper.readTree(expected)), mapper.writeValueAsString(entity));
    }

    @Test
    public void testServerStatus() throws Exception
    {
        ServerStatus status = new ServerStatus("h", 3, "d", true);
        check(status, "{\"code\":3,\"description\":\"d\",\"hostname\":\"h\",\"isLeader\":true}");
    }

    @Test
    public void testSingleElementListStaysAnArray() throws Exception
    {
        check(new IdList(Arrays.asList("a")), "{\"ids\":[\"a\"]}");
        check(new IdList(Arrays.asList("a", "b")), "{\"ids\":[\"a\",\"b\"]}");
    }

    @Test
    public void testNullsAreOmitted() throws Exception
    {
        UITabSpec spec = new UITabSpec();
        spec.setName("N");
        spec.setUrl("u");
        spec.setHtml(true);
        check(spec, "{\"html\":true,\"name\":\"N\",\"url\":\"u\"}");
    }

    @Test
    public void testResultAndNode() throws Exception
    {
        check(new Result("OK", true), "{\"message\":\"OK\",\"succeeded\":true}");

        PathAnalysisNode node = new PathAnalysisNode();
        node.setPath("/x");
        node.setMax(2);
        node.setChildIds(Arrays.asList("c"));
        check(node, "{\"childIds\":[\"c\"],\"max\":2,\"path\":\"/x\"}");
    }

    @Test
    public void testUnknownPropertiesIgnoredOnInput() throws Exception
    {
        UsageListingRequest request = mapper.readValue("{\"startPath\":\"/s\",\"maxChildrenForTraversal\":9,\"extra\":1}", UsageListingRequest.class);
        Assert.assertEquals(request.getStartPath(), "/s");
        Assert.assertEquals(request.getMaxChildrenForTraversal(), 9);
    }
}
