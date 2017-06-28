package manifold.api.gen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 */
public class SrcMemberAccessExpression extends SrcExpression<SrcMemberAccessExpression>
{
  private List<SrcIdentifier> _path = new ArrayList<>();

  public SrcMemberAccessExpression( SrcIdentifier... path )
  {
    Arrays.stream( path ).forEach( e -> _path.add( e ) );
  }
  public SrcMemberAccessExpression( String... path )
  {
    Arrays.stream( path ).forEach( e -> _path.add( new SrcIdentifier( e ) ) );
  }

  public SrcMemberAccessExpression addChild( SrcIdentifier child )
  {
    _path.add( child );
    return this;
  }

  @Override
  public SrcMemberAccessExpression copy()
  {
    SrcIdentifier[] path = new SrcIdentifier[_path.size()];
    for( int i = 0; i < path.length; i++ )
    {
      SrcIdentifier id = path[i];
      path[i] = id.copy();
    }
    return new SrcMemberAccessExpression( path );
  }

  public StringBuilder render( StringBuilder sb, int indent )
  {
    return render( sb, indent, false );
  }

  public StringBuilder render( StringBuilder sb, int indent, boolean sameLine )
  {
    for( int i = 0; i < _path.size(); i++ )
    {
      SrcIdentifier e = _path.get( i );
      if( i > 0 )
      {
        sb.append( '.' );
      }
      e.render( sb, 0 );
    }
    return sb;
  }
}
