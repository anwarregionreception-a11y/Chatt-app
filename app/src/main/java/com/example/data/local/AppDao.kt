package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // Users
    @Query("SELECT * FROM users ORDER BY name ASC")
    fun observeAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    fun observeUser(userId: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: String): UserEntity?

    @Query("SELECT * FROM users WHERE LOWER(email) = LOWER(:identifier) OR mobile_number = :identifier LIMIT 1")
    suspend fun findUserByEmailOrMobile(identifier: String): UserEntity?

    @Query("SELECT COUNT(*) FROM users")
    suspend fun countUsers(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUser(userId: String)

    // Friends
    @Query("SELECT * FROM friend_relations WHERE userA = :userId OR userB = :userId ORDER BY createdAt DESC")
    fun observeFriendRelations(userId: String): Flow<List<FriendRelationEntity>>

    @Query("SELECT * FROM friend_relations WHERE (userA = :u1 AND userB = :u2) OR (userA = :u2 AND userB = :u1) LIMIT 1")
    suspend fun findFriendRelation(u1: String, u2: String): FriendRelationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFriendRelation(relation: FriendRelationEntity)

    @Query("DELETE FROM friend_relations WHERE id = :id")
    suspend fun deleteFriendRelation(id: String)

    // Direct Messages
    @Query("SELECT * FROM direct_messages WHERE senderId = :userId OR receiverId = :userId ORDER BY timestamp DESC")
    fun observeUserDirectMessages(userId: String): Flow<List<DirectMessageEntity>>

    @Query("SELECT * FROM direct_messages WHERE (senderId = :u1 AND receiverId = :u2) OR (senderId = :u2 AND receiverId = :u1) ORDER BY timestamp ASC")
    fun observeConversation(u1: String, u2: String): Flow<List<DirectMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDirectMessage(msg: DirectMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDirectMessages(msgs: List<DirectMessageEntity>)

    // Groups
    @Query("SELECT * FROM groups ORDER BY createdAt DESC")
    fun observeAllGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE id = :groupId LIMIT 1")
    fun observeGroup(groupId: String): Flow<GroupEntity?>

    @Query("SELECT * FROM groups WHERE id = :groupId LIMIT 1")
    suspend fun getGroupById(groupId: String): GroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroup(group: GroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<GroupEntity>)

    // Group Members
    @Query("SELECT * FROM group_members")
    fun observeAllGroupMembers(): Flow<List<GroupMemberEntity>>

    @Query("SELECT * FROM group_members WHERE groupId = :groupId")
    fun observeGroupMembers(groupId: String): Flow<List<GroupMemberEntity>>

    @Query("SELECT * FROM group_members WHERE groupId = :groupId AND userId = :userId LIMIT 1")
    suspend fun getGroupMember(groupId: String, userId: String): GroupMemberEntity?

    @Query("SELECT COUNT(*) FROM group_members WHERE groupId = :groupId AND isBlacklisted = 0")
    suspend fun countActiveGroupMembers(groupId: String): Int

    @Query("SELECT COUNT(*) FROM group_members WHERE groupId = :groupId AND isSupervisor = 1")
    suspend fun countGroupSupervisors(groupId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroupMember(member: GroupMemberEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupMembers(members: List<GroupMemberEntity>)

    @Query("DELETE FROM group_members WHERE groupId = :groupId AND userId = :userId")
    suspend fun removeGroupMember(groupId: String, userId: String)

    // Group Messages
    @Query("SELECT * FROM group_messages WHERE groupId = :groupId ORDER BY timestamp ASC")
    fun observeGroupMessages(groupId: String): Flow<List<GroupMessageEntity>>

    @Query("SELECT * FROM group_messages WHERE groupId = :groupId ORDER BY timestamp DESC LIMIT 10")
    suspend fun getLastTenGroupMessages(groupId: String): List<GroupMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupMessage(msg: GroupMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupMessages(msgs: List<GroupMessageEntity>)

    // Group Donations
    @Query("SELECT * FROM group_donations WHERE groupId = :groupId ORDER BY timestamp DESC")
    fun observeGroupDonations(groupId: String): Flow<List<GroupDonationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupDonation(donation: GroupDonationEntity)

    // Coin Orders
    @Query("SELECT * FROM coin_orders ORDER BY timestamp DESC")
    fun observeAllCoinOrders(): Flow<List<CoinOrderEntity>>

    @Query("SELECT * FROM coin_orders WHERE userId = :userId ORDER BY timestamp DESC")
    fun observeUserCoinOrders(userId: String): Flow<List<CoinOrderEntity>>

    @Query("SELECT * FROM coin_orders WHERE id = :orderId LIMIT 1")
    suspend fun getCoinOrderById(orderId: String): CoinOrderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCoinOrder(order: CoinOrderEntity)

    // System Complaints
    @Query("SELECT * FROM system_complaints WHERE groupOwnerId = :ownerId ORDER BY timestamp DESC")
    fun observeOwnerComplaints(ownerId: String): Flow<List<SystemComplaintEntity>>

    @Query("SELECT * FROM system_complaints ORDER BY timestamp DESC")
    fun observeAllComplaints(): Flow<List<SystemComplaintEntity>>

    @Query("SELECT * FROM system_complaints WHERE id = :complaintId LIMIT 1")
    suspend fun getComplaintById(complaintId: String): SystemComplaintEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertComplaint(complaint: SystemComplaintEntity)

    // Ratings
    @Query("SELECT * FROM app_ratings ORDER BY timestamp DESC")
    fun observeAllRatings(): Flow<List<AppRatingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRating(rating: AppRatingEntity)
}
