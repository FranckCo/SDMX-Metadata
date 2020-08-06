# URIs


## Metadata structure definition

The main resources composing the Metadata structure definition are the MSD itself, the specification and property associated with each SIMSFr attribute, and the Report structure.

### Metadata structure definition

The URI for the SIMS Metadata Structure Definition is http://ec.europa.eu/eurostat/simsv2/msd.

The URI for the SIMSFr Metadata Structure Definition is http://id.insee.fr/qualite/simsv2fr/msd.

### Report structure

The URI for the SIMS Report Structure is http://ec.europa.eu/eurostat/simsv2/reportStructure.

The URI for the SIMSFr Report Structure is http://id.insee.fr/qualite/simsv2fr/reportStructure.

### MetadataAttributeSpecification

SIMS: http://ec.europa.eu/eurostat/simsv2/attributeSpecification/{code}, where {code} is the SIMS attribute code (e.g. S.13.3.2).

SIMSFr: http://id.insee.fr/qualite/simsv2fr/attribut/{code}, where {code} is the SIMSFr attribute code (e.g. I.6.3).

### MetadataAttributeProperty

SIMS: http://ec.europa.eu/eurostat/simsv2/attribute/{code}, where {code} is the SIMS attribute code.

SIMSFr: http://id.insee.fr/qualite/simsv2fr/attribut/{code}, where {code} is the SIMSFr attribute code.

### Concepts

A concept is associated to each attribute property in SIMS and SIMSFr.

SIMS: http://ec.europa.eu/eurostat/simsv2/concept/{code}, where {code} is the SIMS attribute code.

SIMSFr: http://id.insee.fr/concepts/simsv2fr/{code}, where {code} is the SIMSFr attribute code.

All concepts (SIMS and SIMSFr) are grouped in a single concept scheme whose URI is http://id.insee.fr/qualite/simsv2fr/sims.

*TODO:* for better coherence, SIMSFr concepts could be in http://id.insee.fr/qualite/simsv2fr/concept/{code}.

### Quality indicators

Quality indicators defined in the SIMS are represented as instances of the Metric class defined in the [Data Quality Vocabulary](https://www.w3.org/TR/vocab-dqv/). SIMS and SIMSFr concepts (see above) are additionally defined as dqv:Category or dqv:Dimension, depending on the hierarchical position.

The URIs for quality indicators are http://ec.europa.eu/eurostat/simsv2/metric/{code}, where {code} is the SIMS indicator code, for example http://ec.europa.eu/eurostat/simsv2/metric/S.13.2.1. There are no additional indicators defined for the specifically French attributes.

### Publication graph

All SIMSFr MSD resources will be published in the http://rdf.insee.fr/graphes/qualite/simsv2fr graph. Resources associated only with SIMS and not used in SIMSFr are not published for now.


## Code lists

The main resources composing a code list are the code list itself (concept scheme), each code item (concept), and the specific concept shared by all code values.

Two kinds of code lists must be distinguished:

  * The "ordinary" code lists, which correspond to coded values of SIMSFr attributes.
  * The "Thèmes" concept scheme, which is a more high-level hierarchical concept scheme organising the whole statistical domain.

URIs for code lists are formed using the code list French label. More precisely, the following naming constructs are used:

| Label | scheme | code | concept |
|:--|:--|:--|:--|
| Catégorie de source | categoriesSource | categoriesSource |   |
| Fréquence | frequences | frequence | Frequence |
| Mode de collecte | modesCollecte | modeCollecte | ModeCollecte |
| Statut de l'enquête | statutsEnquete | statuEnquete | StatutEnquete |
| Thèmes statistiques | themes | themes | Theme |   |
| Unité enquêtée | unitesEnquetees | uniteEnquetee | UniteEnquetee |


### Concept schemes

For ordinary code lists, the concept scheme URI is http://id.insee.fr/codes/{scheme}, where {scheme} is listed in the table above, for example http://id.insee.fr/codes/unitesEnquetees.

For the "Thèmes" code list, the concept scheme URI is http://id.insee.fr/concepts/themes.

### Codes

For ordinary code lists, the URI for codes is http://id.insee.fr/codes/{code}/{value}, where {code} is listed in the table above, and {value} is the coode value (notation), for example http://id.insee.fr/codes/modeCollecte/P.

For the "Thèmes" code list, the URI for codes is http://id.insee.fr/concepts/themes/{value}, where {value} is the coode value (notation), for example http://id.insee.fr/concepts/theme/POP2.

*TODO:* Fix themes URI, currently the code is in lower case.

### Concepts

For ordinary code lists, the concept URI is http://id.insee.fr/codes/concept/{concept}, where {concept} is listed in the table above, for example http://id.insee.fr/codes/concept/Frequence.

For the "Thèmes" code list, the concept URI is http://id.insee.fr/concepts/themes/Theme.


### Publication graph

The ordinary code lists will be published in the http://rdf.insee.fr/graphes/codes graph. The "Thèmes" concept scheme will be published in the http://rdf.insee.fr/graphes/concepts graph.

## Organizations

### Insee units

URIs for Insee units are http://id.insee.fr/organisations/insee/{identifier}, where {identifier} is the lower cased identifier ("timbre") of the unit, for example http://id.insee.fr/organisations/insee/dg75-e001.

*TODO:* lower case is agains the general rule that URI uses identifier as is.

### Statistical services of ministries

For SSMs or other statistical organisations, the URIs are http://id.insee.fr/organisations/{identifier}, where {identifier} is the "sanitized" (diacritics removed, spaces replaced by dashes) short name or accronym of the organization, for example http://id.insee.fr/organisations/drees.

*TODO:* same remark as above.

### Publication graph

All insee units will be published in the http://rdf.insee.fr/graphes/organisations/insee graph; other organizations will be published in the http://rdf.insee.fr/graphes/organisations graph.

*TODO:* do we need a specific graph for Insee units?

## Statistical operations and related resources

### Families, series and operations

The URIs for families, series and operations are respectively http://id.insee.fr/operations/famille/{identifier}, http://id.insee.fr/operations/serie/{identifier} and http://id.insee.fr/operations/operation/{identifier}, where {identifier} is 's' followed by an integer. The computation of this integer is described in a [specific page](uri-mappings.md).

### Indicators

Indicator URIs are of the form http://id.insee.fr/produits/indicateur/{identifier}, where {identifier} is 'p' followed by an integer.

### Publication graph

Families, series and operations are published in the http://rdf.insee.fr/graphes/operations graph, and indicators are published in the http://rdf.insee.fr/graphes/produits graph.


## Metadata set

MetadataReport: http://id.insee.fr/qualite/rapport/{number}, where {number} is the M0 documentation number (e.g. 1503)
ReportedAttribute: http://id.insee.fr/qualite/attribut/{number}/{code}, where {number} is the M0 documentation number (e.g. 1503) and code is the SIMS attribute code (e.g. S.13.3.2)

DCTypes:Text: http://id.insee.fr/qualite/attribut/{number}/{code}/text