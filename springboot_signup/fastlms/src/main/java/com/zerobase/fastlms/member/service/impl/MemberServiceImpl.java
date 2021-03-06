package com.zerobase.fastlms.member.service.impl;


import com.zerobase.fastlms.admin.dto.MemberDto;
import com.zerobase.fastlms.admin.mapper.MemberMapper;
import com.zerobase.fastlms.components.MailComponents;
import com.zerobase.fastlms.member.entity.Member;
import com.zerobase.fastlms.member.exception.MemberNotEmailAuthException;
import com.zerobase.fastlms.member.model.MemberInput;
import com.zerobase.fastlms.admin.model.MemberParam;
import com.zerobase.fastlms.member.model.ResetPasswordInput;
import com.zerobase.fastlms.member.repository.MemberRepository;
import com.zerobase.fastlms.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class MemberServiceImpl implements MemberService {
    private final MemberRepository memberRepository;
    private final MailComponents mailComponents;
    private final MemberMapper memberMapper;

    @Override
    public boolean register(MemberInput parameter) {

        //동일한 ID가 존재하는지 확인해줘야한다.

        Optional<Member> optionalMember=memberRepository.findById(parameter.getUserId());
        if(optionalMember.isPresent()){
            //데이터가 있다는 것은 현재 userId의 해당하는 데이터가 존재 -> 업데이트 하면 안됌
            return false;
        }
        String encPassword= BCrypt.hashpw(parameter.getPassword(),BCrypt.gensalt());


        String uuid=UUID.randomUUID().toString();
        Member member=Member.builder()
                .userId(parameter.getUserId())
                .userName(parameter.getUserName())
                .phone(parameter.getPhone())
                .password(encPassword)
                .regDt(LocalDateTime.now())
                .emailAuthYn(false)
                .emailAuthKey(uuid)
                .build();

        member.setEmailAuthYn(false);
        //회원가입할 때는 처음 인증 - false로

        memberRepository.save(member);

        String email=parameter.getUserId();
        String subject="fastlms 사이트 가입을 축하드립니다.";
        String text="<p>fastlms 사이트 가입을 축하드립니다.</p><p>아래 링크를 클릭하셔서 가입을 완료하세요<p>"
                + "<div><a target='_blank' href='http://localhost:8080/member/email-auth?id="+uuid+"'>가입 완료</a></div>";
        mailComponents.sendMail(email,subject,text);
        return true;
    }

    @Override
    public boolean emailAuth(String uuid) {
        Optional<Member> optionalMember=memberRepository.findByEmailAuthKey(uuid);
        if(!optionalMember.isPresent()){
            return false;
        }
        Member member=optionalMember.get();

        //이미 활성화 된 경우
        if(member.isEmailAuthYn()){
            return false;
        }
        member.setEmailAuthYn(true);
        member.setEmailAuthDt(LocalDateTime.now());
        memberRepository.save(member);
        return true;
    }

    @Override
    public boolean sendResetPassword(ResetPasswordInput parameter) {
        //일치하는 회원정보 있는지 찾기
        Optional<Member> optionalMember=memberRepository.findByUserIdAndUserName(parameter.getUserId(), parameter.getUserName());
        if(!optionalMember.isPresent()){
            throw new UsernameNotFoundException("회원정보가 없습니다.");
        }
        Member member=optionalMember.get();
        String uuid=UUID.randomUUID().toString();
        //링크를 타고 들어오면 초기화를 진행해주는거.
        member.setResetPasswordKey(uuid);
        member.setResetPasswordLimitDt(LocalDateTime.now().plusDays(1));
        memberRepository.save(member);

        String email=parameter.getUserId();
        String subject="[fastlms]비밀번호 초기화 메일입니다";
        String text="<p>fastlms비밀번호 초기화 메일입니다</p><p>아래 링크를 클릭하셔서 비밀번호를 초기화하세요.<p>"
                + "<div><a target='_blank' href='http://localhost:8080/member/reset/password?id="+uuid+"'>비밀번호초기화링크</a></div>";
        mailComponents.sendMail(email,subject,text);


        return true;
    }

    @Override
    public boolean resetPassword(String uuid, String password) {
        Optional<Member> optionalMember=memberRepository.findByResetPasswordKey(uuid);
        if(!optionalMember.isPresent()){
            throw new UsernameNotFoundException("회원 정보가 존재하지 않습니다.");
        }
        Member member=optionalMember.get();
        //초기화 날짜가 유효한지 체크.
        if(member.getResetPasswordLimitDt()==null){
            throw new RuntimeException("날짜가 유효하지 않습니다.");
        }
        if(member.getResetPasswordLimitDt().isBefore(LocalDateTime.now())){
            throw new RuntimeException("날짜가 유효하지 않습니다.");
        }
        /*password 초기화*/
        String encPassword=BCrypt.hashpw(password,BCrypt.gensalt());
        member.setPassword(encPassword);
        member.setResetPasswordKey("");
        member.setResetPasswordLimitDt(null);
        memberRepository.save(member);
        return true;
    }

    @Override
    public boolean checkResetPassword(String uuid) {
        Optional<Member> optionalMember=memberRepository.findByResetPasswordKey(uuid);
        if(!optionalMember.isPresent()){
            return false;
        }
        Member member=optionalMember.get();
        //초기화 날짜가 유효한지 체크.
        if(member.getResetPasswordLimitDt()==null){
            throw new RuntimeException("날짜가 유효하지 않습니다.");
        }
        if(member.getResetPasswordLimitDt().isBefore(LocalDateTime.now())){
            throw new RuntimeException("날짜가 유효하지 않습니다.");
        }
        return true;
    }

    /*회원의 목록을 모두 가져오는 것*/
    @Override
    public List<MemberDto> list(MemberParam parameter) {
        long totalCount=memberMapper.selectListCount(parameter);
        List<MemberDto> list=memberMapper.selectList(parameter);

        if(CollectionUtils.isEmpty(list)){
            for(MemberDto x:list){
                x.setTotalCount(totalCount);
            }
        }
        return list;
    }


    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        Optional<Member> optionalMember=memberRepository.findById(username);
        //사실 email 찾는거
        if(!optionalMember.isPresent()){
            throw new UsernameNotFoundException("회원정보가 없습니다");
        }
        Member member=optionalMember.get();
        if(!member.isEmailAuthYn()){
            throw new MemberNotEmailAuthException("이메일을 활성화 이후에 로그인을 해주세요.");
        }
        List<GrantedAuthority> grantedAuthorities = new ArrayList<>();
        grantedAuthorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if(member.isAdminYn()){
            grantedAuthorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }


        return new User(member.getUserId(),member.getPassword(),grantedAuthorities);
    }
    //memberservice implement
}
