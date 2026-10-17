package com.sms.announcement;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sms.auth.User;
import com.sms.school.SchoolClass;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    List<Announcement> findAllByOrderByCreatedAtDescIdDesc();

    /** School-wide notices plus those for one class. */
    @Query("""
            select a from Announcement a
            where a.targetClass is null or a.targetClass = :schoolClass
            order by a.createdAt desc, a.id desc""")
    List<Announcement> findForClass(@Param("schoolClass") SchoolClass schoolClass, Limit limit);

    List<Announcement> findByTargetClassIsNullOrderByCreatedAtDescIdDesc(Limit limit);

    /** School-wide notices, notices for any of the given classes, and the user's own posts. */
    @Query("""
            select a from Announcement a
            where a.targetClass is null or a.targetClass.id in :classIds or a.postedBy = :user
            order by a.createdAt desc, a.id desc""")
    List<Announcement> findForTeacher(@Param("classIds") Collection<Long> classIds, @Param("user") User user);
}
