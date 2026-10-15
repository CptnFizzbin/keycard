using System.Xml.Linq;
using Keycard.Errors;
using Keycard.Xml;
using Xunit;

namespace Keycard.Formats.Tests;

public class XmlTests
{
    [Fact]
    public void LoadsAHandWrittenDocument()
    {
        const string xml = """
            <policy>
              <item key="version"><string>0.1</string></item>
              <item key="meta">
                <map><item key="anySubject"><null/></item></map>
              </item>
              <item key="rules">
                <list>
                  <list><string>allow</string><string>Read</string><string>Article</string></list>
                  <list>
                    <string>allow</string><string>Update</string><string>Article</string>
                    <map><item key="owner_id"><number>1</number></item></map>
                  </list>
                </list>
              </item>
            </policy>
            """;

        var definition = PolicyXml.Parse(xml);
        var policy = new Policy(definition);

        Assert.Equal(WildcardToken.Off, definition.Meta!.AnySubject);
        Assert.True(policy.Can("Read", "Article"));
        Assert.True(policy.Can("Update", Subject.Named("Article", new { owner_id = 1 })));
        Assert.False(policy.Can("Update", Subject.Named("Article", new { owner_id = 2 })));
    }

    [Fact]
    public void KeepsStringsAndNumbersApart()
    {
        var definition = new PolicyDefinition
        {
            Rules = { new Rule("allow", "Read", "Article", new Dictionary<string, object?> { ["a"] = "1", ["b"] = 1L, ["c"] = "true", ["d"] = true }) },
        };

        var restored = PolicyXml.Parse(PolicyXml.Serialize(definition));

        Assert.Equal("1", restored.Rules[0].Conditions!["a"]);
        Assert.Equal(1L, restored.Rules[0].Conditions!["b"]);
        Assert.Equal("true", restored.Rules[0].Conditions!["c"]);
        Assert.Equal(true, restored.Rules[0].Conditions!["d"]);
    }

    [Fact]
    public void WritesAPolicyElementWithoutModifyingTheOneRead()
    {
        var element = PolicyXml.Write(new PolicyBuilder().Allow("Read", "Article").BuildDef());
        var before = element.ToString();

        PolicyXml.Read(element);

        Assert.Equal("policy", element.Name.LocalName);
        Assert.Equal(before, element.ToString());
    }

    [Theory]
    [InlineData("<policy>")]
    [InlineData("")]
    public void ReportsMalformedXmlAsALoadError(string xml)
    {
        Assert.Throws<PolicyLoadException>(() => PolicyXml.Parse(xml));
    }

    [Theory]
    [InlineData("<map/>")]
    [InlineData("<policy/>")]
    [InlineData("<policy><rules/></policy>")]
    [InlineData("<policy><item><string>0.1</string></item></policy>")]
    [InlineData("<policy><item key=\"version\"/></policy>")]
    [InlineData("<policy><item key=\"version\"><string>0.1</string><string>x</string></item></policy>")]
    [InlineData("<policy><item key=\"version\"><string>0.1</string></item><item key=\"rules\"><list><list><string>allow</string><string>Read</string><number>x</number></list></list></item></policy>")]
    [InlineData("<policy><item key=\"version\"><string>0.1</string></item><item key=\"x\"><bool>yes</bool></item></policy>")]
    [InlineData("<policy><item key=\"version\"><string>0.1</string></item><item key=\"x\"><blob/></item></policy>")]
    public void ReportsAMalformedDocumentAsALoadError(string xml)
    {
        Assert.Throws<PolicyLoadException>(() => PolicyXml.Parse(xml));
    }

    [Fact]
    public void RejectsANonPolicyRootElement()
    {
        Assert.Throws<PolicyLoadException>(() => PolicyXml.Read(new XElement("rules")));
    }
}
