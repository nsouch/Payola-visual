package cz.payola.common.entities

import scala.collection._
import cz.payola.common.entities.privileges._
import cz.payola.common.entities.plugins.DataSource
import scala.Seq
import cz.payola.common.Entity
import cz.payola.common.entities.settings.OntologyCustomization

/**
  * An entity that may be granted privileges.
  */
trait PrivilegeableEntity extends NamedEntity
{
    /** Type of the privileges. */
    type PrivilegeType <: Privilege[_]

    protected var _privileges: mutable.Buffer[PrivilegeType] = mutable.ArrayBuffer[PrivilegeType]()

    /** Privileges of the entity. */
    def privileges: immutable.Seq[PrivilegeType] = _privileges.toList

    /** Returns the analyses that are accessible for the entity directly via his privileges. */
    def grantedAnalyses: immutable.Seq[Analysis] = {
        privileges.toList.filter(_.isInstanceOf[AccessAnalysisPrivilege]).map(_.asInstanceOf[AccessAnalysisPrivilege].obj)
    }

    /** Returns the plugins that are accessible for the entity directly via his privileges. */
    def grantedPlugins: immutable.Seq[Plugin] = {
        privileges.toList.filter(_.isInstanceOf[UsePluginPrivilege]).map(_.asInstanceOf[UsePluginPrivilege].obj)
    }

    /** Returns the data sources that are accessible for the entity directly via his privileges. */
    def grantedDataSources: immutable.Seq[DataSource] = {
        privileges.toList.filter(_.isInstanceOf[AccessDataSourcePrivilege]).map(_.asInstanceOf[AccessDataSourcePrivilege].obj)
    }

    /** Returns the ontology customizations that are accessible for the entity directly via his privileges. */
    def grantedCustomizations: immutable.Seq[settings.Customization] = {
        privileges.toList.filter(_.isInstanceOf[UseCustomizationPrivilege]).map(_.asInstanceOf[UseCustomizationPrivilege].obj)
    }

    /**
      * Stores the specified privileges to the entity.
      * @param privilege The privilege to store.
      */
    protected def storePrivilege(privilege: PrivilegeType) {
        _privileges += privilege
    }

    /**
      * Discards the privileges from the entity. Complementary operation to store.
      * @param privilege The privilege to discard.
      */
    protected def discardPrivilege(privilege: PrivilegeType) {
        _privileges -= privilege
    }
}
