// Copyright © 2014, German Neuroinformatics Node (G-Node)
//                   A. Stoewer (adrian.stoewer@rz.ifi.lmu.de)
//
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted under the terms of the BSD License. See
// LICENSE file in the root of the Project.

package service

import javax.persistence.{EntityManagerFactory, EntityNotFoundException, NoResultException, Persistence}

import org.junit._
import org.scalatest.junit.JUnitSuite
import play.api.Play
import play.api.test.FakeApplication
import models.{AbstractState, Account}

/**
 * Test for the abstracts service layer
 */
class AbstractServiceTest extends JUnitSuite {

  var srv : AbstractService = _
  var assets: Assets = _

  @Before
  def before() : Unit = {
    assets = new Assets()
    assets.killDB()
    assets.fillDB()
    srv = AbstractService("./figures")
  }

  @Test
  def testList() : Unit = {
    var abstracts = srv.list(assets.conferences(0))
    assert(abstracts.size == assets.abstracts.count{ _.state == AbstractState.Accepted })

    abstracts = srv.list(assets.conferences(1))
    assert(abstracts.size == 0)
  }

  @Test
  def testListAll() : Unit = {
    var abstracts = srv.listAll(assets.conferences(0))
    assert(abstracts.size == assets.abstracts.size)

    abstracts = srv.list(assets.conferences(1))
    assert(abstracts.size == 0)
  }

  @Test
  def testListOwn() : Unit = {
    var abstracts = srv.listOwn(assets.alice)
    assert(abstracts.size == assets.abstracts.size)

    abstracts = srv.listOwn(assets.bob)
    assert(abstracts.size == assets.abstracts.size)

    abstracts = srv.listOwn(assets.eve)
    assert(abstracts.size == 0)
  }

  @Test
  def testGet() : Unit = {
    val abstr = srv.get(assets.abstracts(0).uuid)
    assert(abstr.uuid == assets.abstracts(0).uuid)

    intercept[NoResultException] {
      srv.get(assets.abstracts(1).uuid)
    }
  }

  @Test
  def testGetOwn() : Unit = {
    srv.getOwn(assets.abstracts(0).uuid, assets.alice)

    srv.getOwn(assets.abstracts(0).uuid, assets.admin)

    intercept[EntityNotFoundException] {
      srv.getOwn(
        assets.abstracts(0).uuid,
        Account(Some("uuid"), Some("not@valid.com"))
      )
    }

    intercept[NoResultException] {
      srv.getOwn("NONEXISTENT", assets.eve)
    }

    intercept[IllegalAccessException] {
      srv.getOwn(assets.abstracts(1).uuid, assets.eve)
    }
  }

  @Test
  def testCreate() : Unit = {
    val original = assets.createAbstract()
    val abstr = srv.create(original, assets.conferences(0), assets.alice)

    assert(abstr.uuid != null)
    assert(abstr.title == original.title)
    assert(abstr.text == original.text)

    intercept[EntityNotFoundException] {
      srv.create(original, assets.conferences(0),
                 Account(Some("uuid"), Some("foo@bar.com")))
    }

    original.uuid = "fooid"
    intercept[IllegalArgumentException] {
      srv.create(original, assets.conferences(0), assets.alice)
    }
  }

  @Test
  def testUpdate() : Unit = {
    val original = assets.abstracts(0)

    original.title = "new title"
    original.affiliations.clear()

    val abstr = srv.update(original, assets.alice)

    assert(abstr.title == original.title)
    assert(abstr.affiliations.size == 0)

    val illegal = assets.createAbstract()
    intercept[IllegalArgumentException] {
      srv.update(illegal, assets.alice)
    }

    illegal.uuid = "wringid"
    intercept[EntityNotFoundException] {
      srv.update(illegal, assets.alice)
    }

    intercept[EntityNotFoundException] {
      srv.update(original, Account(Some("uuid"), Some("foo@bar.com")))
    }

    intercept[IllegalAccessException] {
      srv.update(original, assets.eve)
    }
  }

  @Test
  def testDelete() : Unit = {
    val original = assets.abstracts(0)

    intercept[EntityNotFoundException] {
      srv.delete("uuid", assets.alice)
    }

    intercept[EntityNotFoundException] {
      srv.delete(original.uuid, Account(Some("uuid"), Some("foo@bar.com")))
    }

    intercept[IllegalAccessException] {
      srv.delete(original.uuid, assets.eve)
    }

    srv.delete(original.uuid, assets.alice)

    intercept[EntityNotFoundException] {
      srv.delete(original.uuid, assets.alice)
    }
  }

  @Test
  def testPermissions() : Unit = {
    val abstr = assets.abstracts(0) // alice is the only owner
    val alice = assets.alice // or abstr.owners.toList(0)
    val bob = assets.bob
    val eve = assets.eve

    assert(srv.getPermissions(abstr, alice).contains(alice))

    assert(srv.setPermissions(abstr, alice, List[Account](bob, eve)).contains(bob))

    assert(!srv.getPermissions(abstr, bob).contains(alice))

    intercept[IllegalAccessException] {
      srv.setPermissions(abstr, alice, List[Account](alice, bob))
    }

    intercept[IllegalArgumentException] {
      srv.setPermissions(abstr, bob, List[Account]())
    }
  }

  def testStateLogAs(account: Account) {
    val abstr = assets.abstracts(0)
    val state = srv.listStates(abstr.uuid, account)

    assert(state.size > 0)
    assert(state.exists(p => p.state == AbstractState.InPreparation))
  }

  @Test
  def testStateLog() : Unit = {
    testStateLogAs(assets.alice)
    testStateLogAs(assets.bob) // bob is abstract owner, but not conf or site admin
    testStateLogAs(assets.dave) //dave is conf owner, but not site admin or abstract owner
  }

  @Test
  def testSetState() {
    val abstr = assets.abstracts(0)
    val oldState = abstr.state

    val stateLog = srv.setState(abstr, AbstractState.Accepted, assets.alice, Some("ServiceTest: Abstract accepted"))
    assert(stateLog.head.state == AbstractState.Accepted)

    val statesFromService = srv.listStates(abstr.uuid, assets.alice)
    assert(statesFromService.length == stateLog.length)

    //both must be sorted and contain the same entries
    for ((a, b) <- statesFromService.zip(stateLog)) {
      assert(a.uuid == b.uuid)
    }

    //Set abstract back to old state
    srv.setState(abstr, oldState, assets.alice, Some("ServiceTest: Abstract published again"))

  }

  @Test
  def testPatch() {
    val abstr = assets.abstracts(0)
    val updates = List(PatchAddSortId(1), PatchAddDOI("10.12751/nncn.test.0001"))
    val newAbstr = srv.patch(abstr, updates)

    assert(newAbstr.sortId == 1)
    assert(newAbstr.doi == "10.12751/nncn.test.0001")
  }

}


object AbstractServiceTest {

  var app: FakeApplication = null

  @BeforeClass
  def beforeClass() = {
    app = new FakeApplication()
    Play.start(app)
  }

  @AfterClass
  def afterClass() = {
    Play.stop()
  }

}
